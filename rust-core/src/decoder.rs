use std::cell::UnsafeCell;
use std::fs::File;
use std::path::Path;
use std::sync::atomic::{AtomicBool, AtomicI64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::Duration;

use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, SeekMode, SeekTo};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use symphonia::core::units::Time;

pub const RING_BUFFER_CAPACITY: usize = 262_144;
const RING_BUFFER_MASK: usize = RING_BUFFER_CAPACITY - 1;

/// 无锁 SPSC (单生产者单消费者) 环形音频缓冲区
/// - 生产者：后台 Symphonia 解码线程
/// - 消费者：Oboe 实时音频渲染回调线程（最高音频优先级，绝不发生互斥锁阻塞）
pub struct AudioRingBuffer {
    storage: UnsafeCell<[f32; RING_BUFFER_CAPACITY]>,
    read_index: AtomicUsize,
    write_index: AtomicUsize,
}

unsafe impl Sync for AudioRingBuffer {}
unsafe impl Send for AudioRingBuffer {}

impl AudioRingBuffer {
    pub fn new() -> Self {
        Self {
            storage: UnsafeCell::new([0.0f32; RING_BUFFER_CAPACITY]),
            read_index: AtomicUsize::new(0),
            write_index: AtomicUsize::new(0),
        }
    }

    pub fn available_read(&self) -> usize {
        let w = self.write_index.load(Ordering::Acquire);
        let r = self.read_index.load(Ordering::Relaxed);
        w.saturating_sub(r)
    }

    pub fn available_write(&self) -> usize {
        let r = self.read_index.load(Ordering::Acquire);
        let w = self.write_index.load(Ordering::Relaxed);
        RING_BUFFER_CAPACITY - (w.saturating_sub(r))
    }

    /// 生产者写入立体声交错采样数据（纯原子序号推进，无竞态）
    pub fn write_interleaved(&self, data: &[f32]) -> usize {
        let avail = self.available_write();
        let to_write = data.len().min(avail);
        let mut w = self.write_index.load(Ordering::Relaxed);
        let ptr = self.storage.get();

        unsafe {
            for &sample in &data[..to_write] {
                (*ptr)[w & RING_BUFFER_MASK] = sample;
                w += 1;
            }
        }

        self.write_index.store(w, Ordering::Release);
        to_write
    }

    /// 消费者从缓冲区拉取待播放采样点
    pub fn read_interleaved(&self, dest: &mut [f32]) -> usize {
        let avail = self.available_read();
        let to_read = dest.len().min(avail);
        let mut r = self.read_index.load(Ordering::Relaxed);
        let ptr = self.storage.get();

        unsafe {
            for sample in dest.iter_mut().take(to_read) {
                *sample = (*ptr)[r & RING_BUFFER_MASK];
                r += 1;
            }
        }

        self.read_index.store(r, Ordering::Release);
        to_read
    }

    pub fn reset(&self) {
        let w = self.write_index.load(Ordering::Acquire);
        self.read_index.store(w, Ordering::Release);
    }
}

pub struct AudioPlayerEngine {
    ring_buffer: Arc<AudioRingBuffer>,
    is_playing: Arc<AtomicBool>,
    should_stop_thread: Arc<AtomicBool>,
    seek_target_ms: Arc<AtomicI64>,
    current_pos_ms: Arc<AtomicI64>,
    duration_ms: Arc<AtomicI64>,
    worker_handle: Option<JoinHandle<()>>,
    target_sample_rate: u32,
}

impl AudioPlayerEngine {
    pub fn new(target_sample_rate: u32) -> Self {
        Self {
            ring_buffer: Arc::new(AudioRingBuffer::new()),
            is_playing: Arc::new(AtomicBool::new(false)),
            should_stop_thread: Arc::new(AtomicBool::new(false)),
            seek_target_ms: Arc::new(AtomicI64::new(-1)),
            current_pos_ms: Arc::new(AtomicI64::new(0)),
            duration_ms: Arc::new(AtomicI64::new(0)),
            worker_handle: None,
            target_sample_rate,
        }
    }

    pub fn set_playing(&self, playing: bool) {
        self.is_playing.store(playing, Ordering::SeqCst);
    }

    pub fn is_playing(&self) -> bool {
        self.is_playing.load(Ordering::Relaxed)
    }

    pub fn get_position_ms(&self) -> i64 {
        self.current_pos_ms.load(Ordering::Relaxed)
    }

    pub fn get_duration_ms(&self) -> i64 {
        self.duration_ms.load(Ordering::Relaxed)
    }

    pub fn seek_to_ms(&self, target_ms: i64) {
        self.seek_target_ms.store(target_ms.max(0), Ordering::SeqCst);
    }

    /// 打开指定音频文件路径并启动后台多格式解码管道
    pub fn open_file(&mut self, file_path: &str) -> Result<(), String> {
        self.stop();

        if !Path::new(file_path).exists() {
            return Err(format!("音频文件不存在: {}", file_path));
        }

        self.ring_buffer.reset();
        self.should_stop_thread.store(false, Ordering::SeqCst);
        self.seek_target_ms.store(-1, Ordering::SeqCst);
        self.current_pos_ms.store(0, Ordering::SeqCst);
        self.duration_ms.store(0, Ordering::SeqCst);

        let path_owned = file_path.to_string();
        let ring_buf = Arc::clone(&self.ring_buffer);
        let is_playing = Arc::clone(&self.is_playing);
        let should_stop = Arc::clone(&self.should_stop_thread);
        let seek_target = Arc::clone(&self.seek_target_ms);
        let current_pos = Arc::clone(&self.current_pos_ms);
        let duration_atomic = Arc::clone(&self.duration_ms);
        let target_rate = self.target_sample_rate;

        let handle = thread::Builder::new()
            .name("qianqian_decode".into())
            .spawn(move || {
                decode_worker_loop(
                    path_owned,
                    ring_buf,
                    is_playing,
                    should_stop,
                    seek_target,
                    current_pos,
                    duration_atomic,
                    target_rate,
                );
            })
            .map_err(|e| format!("启动解码线程失败: {}", e))?;

        self.worker_handle = Some(handle);
        self.is_playing.store(true, Ordering::SeqCst);
        Ok(())
    }

    pub fn stop(&mut self) {
        self.is_playing.store(false, Ordering::SeqCst);
        self.should_stop_thread.store(true, Ordering::SeqCst);
        if let Some(handle) = self.worker_handle.take() {
            let _ = handle.join();
        }
        self.ring_buffer.reset();
    }

    /// 供 Oboe 音频回调消费立体声交错采样
    pub fn fill_audio_frame(&self, out_stereo: &mut [f32]) -> usize {
        if !self.is_playing.load(Ordering::Relaxed) {
            out_stereo.fill(0.0);
            return 0;
        }

        let read_count = self.ring_buffer.read_interleaved(out_stereo);
        if read_count < out_stereo.len() {
            // 欠载保护（静音补齐防爆音）
            out_stereo[read_count..].fill(0.0);
        }

        // 推进播放毫秒进度
        let frames = (read_count / 2) as i64;
        let delta_ms = (frames * 1000) / (self.target_sample_rate as i64);
        self.current_pos_ms.fetch_add(delta_ms, Ordering::Relaxed);

        read_count
    }
}

/// 后台无损/多格式解码主循环：支持 FLAC, MP3, AAC, WAV, OGG 等
fn decode_worker_loop(
    file_path: String,
    mut ring_buf: Arc<AudioRingBuffer>,
    is_playing: Arc<AtomicBool>,
    should_stop: Arc<AtomicBool>,
    seek_target: Arc<AtomicI64>,
    current_pos: Arc<AtomicI64>,
    duration_atomic: Arc<AtomicI64>,
    target_rate: u32,
) {
    let file = match File::open(&file_path) {
        Ok(f) => f,
        Err(_) => return,
    };

    let mss = MediaSourceStream::new(Box::new(file), Default::default());
    let mut hint = Hint::new();
    if let Some(ext) = Path::new(&file_path).extension().and_then(|s| s.to_str()) {
        hint.with_extension(ext);
    }

    let meta_opts: MetadataOptions = Default::default();
    let fmt_opts: FormatOptions = Default::default();

    let probed = match symphonia::default::get_probe().format(&hint, mss, &fmt_opts, &meta_opts) {
        Ok(p) => p,
        Err(_) => return,
    };

    let mut format = probed.format;
    let track = match format.default_track() {
        Some(t) => t,
        None => return,
    };

    let track_id = track.id;
    let codec_params = track.codec_params.clone();

    // 计算总时长
    if let (Some(n_frames), Some(tb)) = (codec_params.n_frames, codec_params.time_base) {
        let time = tb.calc_time(n_frames);
        let total_ms = (time.seconds * 1000) + ((time.frac * 1000.0) as u64);
        duration_atomic.store(total_ms as i64, Ordering::Release);
    }

    let dec_opts: DecoderOptions = Default::default();
    let mut decoder = match symphonia::default::get_codecs().make(&codec_params, &dec_opts) {
        Ok(d) => d,
        Err(_) => return,
    };

    let mut sample_buf: Option<SampleBuffer<f32>> = None;
    let mut resample_temp = Vec::with_capacity(4096);
    let mut src_channels = 2usize;
    let mut src_rate = target_rate;

    while !should_stop.load(Ordering::Relaxed) {
        // 1. 检查是否存在 Seek 请求
        let seek_ms = seek_target.swap(-1, Ordering::SeqCst);
        if seek_ms >= 0 {
            let target_secs = (seek_ms as f64) / 1000.0;
            let seek_to = SeekTo::Time {
                time: Time::from(target_secs),
                track_id: Some(track_id),
            };
            if format.seek(SeekMode::Coarse, seek_to).is_ok() {
                decoder.reset();
                ring_buf.reset();
                current_pos.store(seek_ms, Ordering::Release);
            }
        }

        // 2. 检查缓冲空间，如果缓冲区剩余充足（> 1.5 秒），适度休眠防止占用过多 CPU
        if ring_buf.available_write() < 16384 {
            thread::sleep(Duration::from_millis(25));
            continue;
        }

        // 3. 读取下一个数据包并解码
        let packet = match format.next_packet() {
            Ok(pkt) => pkt,
            Err(SymphoniaError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                // 音频正常播放完毕
                is_playing.store(false, Ordering::Release);
                break;
            }
            Err(_) => break,
        };

        if packet.track_id() != track_id {
            continue;
        }

        match decoder.decode(&packet) {
            Ok(audio_buf_ref) => {
                if sample_buf.is_none() {
                    let spec = *audio_buf_ref.spec();
                    src_channels = spec.channels.count();
                    src_rate = spec.rate;
                    let duration = audio_buf_ref.capacity() as u64;
                    sample_buf = Some(SampleBuffer::<f32>::new(duration, spec));
                }

                if let Some(ref mut sbuf) = sample_buf {
                    sbuf.copy_interleaved_ref(audio_buf_ref);
                    let raw_samples = sbuf.samples();

                    // 转换为 48kHz 标准立体声交错浮点格式
                    resample_temp.clear();
                    convert_and_resample_to_stereo(
                        raw_samples,
                        src_channels,
                        src_rate,
                        target_rate,
                        &mut resample_temp,
                    );

                    // 写入无锁环形缓冲区
                    let mut written = 0;
                    while written < resample_temp.len() && !should_stop.load(Ordering::Relaxed) {
                        let n = ring_buf.write_interleaved(&resample_temp[written..]);
                        written += n;
                        if written < resample_temp.len() {
                            thread::sleep(Duration::from_millis(5));
                        }
                    }
                }
            }
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(_) => break,
        }
    }
}

/// 快速高质量重采样与单/多声道转立体声
fn convert_and_resample_to_stereo(
    input: &[f32],
    src_channels: usize,
    src_rate: u32,
    target_rate: u32,
    output: &mut Vec<f32>,
) {
    if input.is_empty() {
        return;
    }

    if src_rate == target_rate {
        match src_channels {
            1 => {
                output.reserve(input.len() * 2);
                for &sample in input {
                    output.push(sample);
                    output.push(sample);
                }
            }
            2 => {
                output.extend_from_slice(input);
            }
            _ => {
                // 大于 2 声道仅提取左和右通道
                let frames = input.len() / src_channels;
                output.reserve(frames * 2);
                for i in 0..frames {
                    output.push(input[i * src_channels]);
                    output.push(input[i * src_channels + 1]);
                }
            }
        }
        return;
    }

    // 线性插值重采样
    let ratio = (src_rate as f64) / (target_rate as f64);
    let src_frames = input.len() / src_channels;
    let out_frames = ((src_frames as f64) / ratio).floor() as usize;
    output.reserve(out_frames * 2);

    for out_i in 0..out_frames {
        let src_pos = (out_i as f64) * ratio;
        let idx0 = src_pos.floor() as usize;
        let frac = (src_pos - (idx0 as f64)) as f32;
        let idx1 = (idx0 + 1).min(src_frames - 1);

        let (l0, r0) = if src_channels == 1 {
            (input[idx0], input[idx0])
        } else {
            (input[idx0 * src_channels], input[idx0 * src_channels + 1])
        };

        let (l1, r1) = if src_channels == 1 {
            (input[idx1], input[idx1])
        } else {
            (input[idx1 * src_channels], input[idx1 * src_channels + 1])
        };

        let out_l = l0 + frac * (l1 - l0);
        let out_r = r0 + frac * (r1 - r0);
        output.push(out_l);
        output.push(out_r);
    }
}
