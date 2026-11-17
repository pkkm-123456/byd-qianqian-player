use rustfft::{num_complex::Complex, FftPlanner};

const MAX_DELAY_SAMPLES: usize = 512;
const FFT_SIZE: usize = 1024;
pub const MAX_SPECTRUM_BANDS: usize = 64;

/// 比亚迪全系车型音响品牌与声场配置预设
#[repr(i32)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CarAudioPreset {
    /// 1. 比亚迪原厂标准 4/6/8 扬声器（秦PLUS/宋Pro/宋Plus燃油/海豚/海鸥/元等）：
    /// 门板喇叭偏低易下沉、无外部功放。
    /// -> 启用 1.8ms 皇帝位延时 + 1.45x 环绕扩宽 + 140Hz 低频单声道防车门共振 + 1.2x 人声抬升
    BydStandard = 0,

    /// 2. Dirac 数字调音系统（宋L/元PLUS/海豹后驱基础版等）：
    /// 车机已自带 Dirac 冲激响应滤波。
    /// -> 彻底关闭软件延时 (0ms) 避免滤波打架 + 1.20x 适度自然微环绕 + 120Hz 单声道
    DiracTuned = 1,

    /// 3. Dynaudio 丹拿 Hi-Fi 原音直通（汉EV/DM-i、唐DM-p、海豹四驱专属）：
    /// 车机搭载 12/16/22 丹拿定制独立功放（自带主驾聚焦声场）。
    /// -> 延时直通 (0ms) + 1.05x 纯净微拓宽 + 全频立体声保真，释放丹拿丝膜高音与大动态
    DynaudioDirect = 2,

    /// 4. Dynaudio 丹拿 3D 音乐厅模式：
    /// -> 0ms 延时 + 1.30x 开阔宏大空间混响 + 100Hz 低频延伸
    DynaudioConcert = 3,

    /// 5. Devialet 腾势帝瓦雷 SAM 系统（腾势 N7/N8/D9/Z9GT 专属）：
    /// 搭载法国帝瓦雷低音炮与超高功率功放。
    /// -> 0ms 延时 + 1.15x 声场 + 80Hz 极低频单声道（释放帝瓦雷爆炸级下潜）
    DevialetSam = 4,

    /// 6. 纯净监听直通（仰望 U8/U7 证据音响 / 发烧友母带直通）：
    /// -> 100% 旁路任何声场与低频修饰，仅保留 32 段音柱频谱分析与导航静音压音
    StudioPure = 5,
}

impl CarAudioPreset {
    pub fn from_i32(val: i32) -> Self {
        match val {
            0 => CarAudioPreset::BydStandard,
            1 => CarAudioPreset::DiracTuned,
            2 => CarAudioPreset::DynaudioDirect,
            3 => CarAudioPreset::DynaudioConcert,
            4 => CarAudioPreset::DevialetSam,
            5 => CarAudioPreset::StudioPure,
            _ => CarAudioPreset::BydStandard,
        }
    }
}

pub struct CarDspPipeline {
    pub sample_rate: usize,
    left_delay_line: [f32; MAX_DELAY_SAMPLES],
    delay_write_idx: usize,
    delay_samples: usize,
    pub driver_delay_enabled: bool,
    pub stereo_width: f32,
    pub bass_mono_enabled: bool,
    pub lp_alpha: f32,
    pub presence_gain: f32,
    pub current_preset: CarAudioPreset,

    lowpass_l: f32,
    lowpass_r: f32,
    current_gain: f32,
    fft_ring: [f32; FFT_SIZE],
    fft_write_idx: usize,
    pub spectrum_bars: [f32; MAX_SPECTRUM_BANDS],
    pub spectrum_peaks: [f32; MAX_SPECTRUM_BANDS],
    peak_hold_ticks: [u8; MAX_SPECTRUM_BANDS],
}

impl CarDspPipeline {
    pub fn new(sample_rate: usize, driver_delay_ms: f32, stereo_width: f32) -> Self {
        let delay_samples =
            ((driver_delay_ms * 0.001 * (sample_rate as f32)) as usize).min(MAX_DELAY_SAMPLES - 1);
        let mut dsp = Self {
            sample_rate,
            left_delay_line: [0.0; MAX_DELAY_SAMPLES],
            delay_write_idx: 0,
            delay_samples,
            driver_delay_enabled: true,
            stereo_width,
            bass_mono_enabled: true,
            lp_alpha: 0.0183, // 默认 140Hz
            presence_gain: 1.0,
            current_preset: CarAudioPreset::BydStandard,
            lowpass_l: 0.0,
            lowpass_r: 0.0,
            current_gain: 1.0,
            fft_ring: [0.0; FFT_SIZE],
            fft_write_idx: 0,
            spectrum_bars: [0.0; MAX_SPECTRUM_BANDS],
            spectrum_peaks: [0.0; MAX_SPECTRUM_BANDS],
            peak_hold_ticks: [0; MAX_SPECTRUM_BANDS],
        };
        // 初始应用标准原厂预设
        dsp.apply_preset(CarAudioPreset::BydStandard);
        dsp
    }

    /// 应用指定车型音响品类声场预设
    pub fn apply_preset(&mut self, preset: CarAudioPreset) {
        self.current_preset = preset;
        let fs = self.sample_rate as f32;

        match preset {
            CarAudioPreset::BydStandard => {
                self.driver_delay_enabled = true;
                self.delay_samples = ((1.8 * 0.001 * fs) as usize).min(MAX_DELAY_SAMPLES - 1);
                self.stereo_width = 1.45;
                self.bass_mono_enabled = true;
                self.lp_alpha = (2.0 * std::f32::consts::PI * 140.0 / fs).clamp(0.005, 0.1);
                self.presence_gain = 1.20; // 适当抬升中高频人声高度
            }
            CarAudioPreset::DiracTuned => {
                self.driver_delay_enabled = false;
                self.delay_samples = 0;
                self.stereo_width = 1.20;
                self.bass_mono_enabled = true;
                self.lp_alpha = (2.0 * std::f32::consts::PI * 120.0 / fs).clamp(0.005, 0.1);
                self.presence_gain = 1.0;
            }
            CarAudioPreset::DynaudioDirect => {
                self.driver_delay_enabled = false;
                self.delay_samples = 0;
                self.stereo_width = 1.05;
                self.bass_mono_enabled = false; // 全频段立体声，完全交给原车丹拿硬件功放分频
                self.presence_gain = 1.0;
            }
            CarAudioPreset::DynaudioConcert => {
                self.driver_delay_enabled = false;
                self.delay_samples = 0;
                self.stereo_width = 1.30;
                self.bass_mono_enabled = true;
                self.lp_alpha = (2.0 * std::f32::consts::PI * 100.0 / fs).clamp(0.005, 0.1);
                self.presence_gain = 1.05;
            }
            CarAudioPreset::DevialetSam => {
                self.driver_delay_enabled = false;
                self.delay_samples = 0;
                self.stereo_width = 1.15;
                self.bass_mono_enabled = true;
                self.lp_alpha = (2.0 * std::f32::consts::PI * 80.0 / fs).clamp(0.005, 0.1); // 释放 80Hz 以下超重低音
                self.presence_gain = 1.0;
            }
            CarAudioPreset::StudioPure => {
                self.driver_delay_enabled = false;
                self.delay_samples = 0;
                self.stereo_width = 1.0;
                self.bass_mono_enabled = false;
                self.presence_gain = 1.0;
            }
        }
    }

    pub fn set_parameters(&mut self, delay_enabled: bool, delay_ms: f32, stereo_width: f32) {
        self.driver_delay_enabled = delay_enabled;
        self.delay_samples =
            ((delay_ms * 0.001 * (self.sample_rate as f32)) as usize).min(MAX_DELAY_SAMPLES - 1);
        self.stereo_width = stereo_width.clamp(0.0, 3.0);
    }

    /// 处理立体声交错浮点音频帧
    pub fn process_interleaved(&mut self, pcm: &mut [f32], target_duck_gain: f32) {
        let is_bypass = self.current_preset == CarAudioPreset::StudioPure;

        for frame in pcm.chunks_exact_mut(2) {
            self.current_gain += 0.0015 * (target_duck_gain - self.current_gain);

            let in_l = frame[0] * self.current_gain;
            let in_r = frame[1] * self.current_gain;

            let (wide_l, wide_r) = if is_bypass {
                (in_l, in_r)
            } else {
                // 1. 低频单声道锚定与防车门共振
                self.lowpass_l += self.lp_alpha * (in_l - self.lowpass_l);
                self.lowpass_r += self.lp_alpha * (in_r - self.lowpass_r);

                let (bass_l, bass_r) = if self.bass_mono_enabled {
                    let mono = 0.5 * (self.lowpass_l + self.lowpass_r);
                    (mono, mono)
                } else {
                    (self.lowpass_l, self.lowpass_r)
                };

                let high_l = (in_l - self.lowpass_l) * self.presence_gain;
                let high_r = (in_r - self.lowpass_r) * self.presence_gain;

                // 2. Mid-Side 立体声 3D 拓宽
                let mid = 0.5 * (high_l + high_r);
                let side = 0.5 * (high_l - high_r) * self.stereo_width;

                (bass_l + (mid + side), bass_r + (mid - side))
            };

            // 3. 主驾左声道时间延时对齐（若已关闭则完全直通）
            let effective_l = if self.driver_delay_enabled {
                self.left_delay_line[self.delay_write_idx] = wide_l;
                let read_idx = (self.delay_write_idx + MAX_DELAY_SAMPLES - self.delay_samples)
                    % MAX_DELAY_SAMPLES;
                let aligned = self.left_delay_line[read_idx];
                self.delay_write_idx = (self.delay_write_idx + 1) % MAX_DELAY_SAMPLES;
                aligned
            } else {
                wide_l
            };

            let out_l = soft_limit_03dbfs(effective_l);
            let out_r = soft_limit_03dbfs(wide_r);
            frame[0] = out_l;
            frame[1] = out_r;

            // 4. 写入 FFT 环形缓冲用于千千静听高密度跳动音柱计算
            self.fft_ring[self.fft_write_idx] = 0.5 * (out_l + out_r);
            self.fft_write_idx = (self.fft_write_idx + 1) % FFT_SIZE;
            if self.fft_write_idx == 0 {
                self.compute_spectrum();
            }
        }
    }

    /// 执行 1024 点汉宁窗加权 FFT 频域能量转换，并对数划分为 48 个高密度频段
    fn compute_spectrum(&mut self) {
        let mut planner = FftPlanner::<f32>::new();
        let fft = planner.plan_fft_forward(FFT_SIZE);

        let mut buffer = [Complex { re: 0.0f32, im: 0.0f32 }; FFT_SIZE];
        for i in 0..FFT_SIZE {
            // 汉宁窗 (Hanning Window) 消除帧边缘突变，抑制频域频谱泄漏
            let hann = 0.5
                * (1.0 - (2.0 * std::f32::consts::PI * (i as f32) / (FFT_SIZE as f32)).cos());
            buffer[i].re = self.fft_ring[i] * hann;
        }

        fft.process(&mut buffer[..]);

        let bin_hz = (self.sample_rate as f32) / (FFT_SIZE as f32);

        // 对数划分 48 个频段 (20Hz ~ 20kHz 完整人耳可听域)
        for band in 0..48 {
            let f_start = 22.0 * 1.152f32.powi(band as i32);
            let f_end = 22.0 * 1.152f32.powi((band + 1) as i32);
            let start_bin = ((f_start / bin_hz).floor() as usize).clamp(1, FFT_SIZE / 2 - 2);
            let end_bin = ((f_end / bin_hz).ceil() as usize).clamp(start_bin, FFT_SIZE / 2 - 1);

            let mut sum_mag = 0.0f32;
            for bin in start_bin..=end_bin {
                let c = buffer[bin];
                sum_mag += (c.re * c.re + c.im * c.im).sqrt();
            }
            let avg_mag = sum_mag / ((end_bin - start_bin + 1) as f32);
            // 动态范围分贝映射 (-65dBFS ~ -10dBFS 线性映射至 0.0 ~ 1.0)
            let db = 20.0 * (avg_mag / (FFT_SIZE as f32) + 1e-6).log10();
            let normalized = ((db + 65.0) / 55.0).clamp(0.0, 1.0);

            // 经典千千静听弹道学阻尼：
            // 1. 瞬态起跳 (Fast Attack): 瞬时爆发系数 0.75，随歌曲底鼓与主唱声浪瞬间拔地而起
            if normalized > self.spectrum_bars[band] {
                self.spectrum_bars[band] = 0.75 * normalized + 0.25 * self.spectrum_bars[band];
            } else {
                // 2. 平滑回落 (Smooth Decay): 避免高频闪烁疲劳
                self.spectrum_bars[band] = (self.spectrum_bars[band] - 0.030).max(normalized);
            }

            // 3. 千千静听经典顶部黄绿峰值帽 (Peak Hold & Gravity Drop)
            if self.spectrum_bars[band] >= self.spectrum_peaks[band] {
                self.spectrum_peaks[band] = self.spectrum_bars[band];
                self.peak_hold_ticks[band] = 5; // 峰值短暂悬停 (约 110ms)
            } else if self.peak_hold_ticks[band] > 0 {
                self.peak_hold_ticks[band] -= 1; // 保持悬停
            } else {
                // 悬停结束，重力加速度自然滑落
                self.spectrum_peaks[band] = (self.spectrum_peaks[band] - 0.012).max(0.0);
            }
        }
    }
}

#[inline(always)]
fn soft_limit_03dbfs(x: f32) -> f32 {
    let ceiling = 0.966; // -0.3 dBFS
    if x.abs() <= 0.75 {
        x
    } else {
        ceiling * (x / ceiling).tanh()
    }
}
