pub mod decoder;
pub mod dsp;
pub mod lyrics_db;
pub mod nas_cache;

use decoder::AudioPlayerEngine;
use dsp::CarDspPipeline;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, jstring};
use jni::JNIEnv;
use lyrics_db::LyricsDatabase;
use nas_cache::NasCacheManager;
use once_cell::sync::Lazy;
use std::ffi::CStr;
use std::fs;
use std::os::raw::c_char;
use std::path::Path;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::Mutex;

static IS_ENGINE_READY: AtomicBool = AtomicBool::new(false);
static DUCKING_GAIN_BITS: AtomicU32 = AtomicU32::new(0x3F800000); // 1.0f

static DSP_PIPELINE: Lazy<Mutex<CarDspPipeline>> =
    Lazy::new(|| Mutex::new(CarDspPipeline::new(48000, 1.8, 1.35)));
static PLAYER_ENGINE: Lazy<Mutex<AudioPlayerEngine>> =
    Lazy::new(|| Mutex::new(AudioPlayerEngine::new(48000)));
static LYRICS_DB: Lazy<Mutex<Option<LyricsDatabase>>> = Lazy::new(|| Mutex::new(None));
static NAS_MANAGER: Lazy<Mutex<Option<NasCacheManager>>> = Lazy::new(|| Mutex::new(None));

static CURRENT_SONG_ID: Lazy<Mutex<String>> = Lazy::new(|| Mutex::new(String::new()));
static CURRENT_SONG_TITLE: Lazy<Mutex<String>> = Lazy::new(|| Mutex::new(String::new()));
static CURRENT_SONG_ARTIST: Lazy<Mutex<String>> = Lazy::new(|| Mutex::new(String::new()));

// =========================================================================
// C ABI 供 Oboe C++ 音频渲染管道调用
// =========================================================================

#[no_mangle]
pub extern "C" fn rust_engine_init(
    sample_rate: i32,
    driver_delay_ms: f32,
    stereo_width: f32,
    cache_dir: *const c_char,
    db_path: *const c_char,
    max_cache_bytes: i64,
) {
    let cache_str = unsafe { CStr::from_ptr(cache_dir).to_string_lossy().into_owned() };
    let db_str = unsafe { CStr::from_ptr(db_path).to_string_lossy().into_owned() };

    let srate = if sample_rate > 0 { sample_rate as usize } else { 48000 };

    if let Ok(mut dsp) = DSP_PIPELINE.lock() {
        *dsp = CarDspPipeline::new(srate, driver_delay_ms, stereo_width);
    }

    if let Ok(mut player) = PLAYER_ENGINE.lock() {
        *player = AudioPlayerEngine::new(srate as u32);
    }

    if let Ok(db) = LyricsDatabase::open(&db_str) {
        if let Ok(mut guard) = LYRICS_DB.lock() {
            *guard = Some(db);
        }
    }

    if let Ok(mut guard) = NAS_MANAGER.lock() {
        *guard = Some(NasCacheManager::new(
            cache_str,
            db_str,
            max_cache_bytes as u64,
        ));
    }

    IS_ENGINE_READY.store(true, Ordering::SeqCst);
}

#[no_mangle]
pub extern "C" fn rust_engine_set_playing(playing: bool) {
    if let Ok(player) = PLAYER_ENGINE.lock() {
        player.set_playing(playing);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_seek_ms(pos_ms: i64) {
    if let Ok(player) = PLAYER_ENGINE.lock() {
        player.seek_to_ms(pos_ms);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_skip_next() {
    if let Ok(player) = PLAYER_ENGINE.lock() {
        player.seek_to_ms(0);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_skip_prev() {
    if let Ok(player) = PLAYER_ENGINE.lock() {
        player.seek_to_ms(0);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_set_ducking(target_gain: f32, _ramp_ms: i32) {
    DUCKING_GAIN_BITS.store(target_gain.clamp(0.0, 1.0).to_bits(), Ordering::Relaxed);
}

/// Oboe 实时音频渲染回调：
/// 1. 从 Symphonia 后台多格式解码管道拉取真实 PCM 浮点音频帧填充 slice
/// 2. 注入车规级 DSP（导航平滑压音 + 低频单声道防共振 + M/S 3D 环绕 + 主驾皇帝位延时）
/// 3. 输出至车机车载硬件声卡
#[no_mangle]
pub extern "C" fn rust_engine_render_audio(out_interleaved_stereo: *mut f32, num_frames: i32) {
    let total_samples = (num_frames as usize) * 2;
    let slice = unsafe { std::slice::from_raw_parts_mut(out_interleaved_stereo, total_samples) };

    // 1. 从播放解码引擎提取已解码的 PCM 音频样本
    let mut filled = false;
    if let Ok(player) = PLAYER_ENGINE.try_lock() {
        if player.is_playing() {
            player.fill_audio_frame(slice);
            filled = true;
        }
    }

    if !filled {
        slice.fill(0.0);
        return;
    }

    // 2. 车规 DSP 处理
    let duck_gain = f32::from_bits(DUCKING_GAIN_BITS.load(Ordering::Relaxed));
    if let Ok(mut dsp) = DSP_PIPELINE.try_lock() {
        dsp.process_interleaved(slice, duck_gain);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_pull_spectrum32(out_bars32: *mut f32, out_peaks32: *mut f32) {
    let bars = unsafe { std::slice::from_raw_parts_mut(out_bars32, 32) };
    let peaks = unsafe { std::slice::from_raw_parts_mut(out_peaks32, 32) };
    if let Ok(dsp) = DSP_PIPELINE.try_lock() {
        bars.copy_from_slice(&dsp.spectrum_bars[..32]);
        peaks.copy_from_slice(&dsp.spectrum_peaks[..32]);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_pull_spectrum_dyn(out_bars: *mut f32, out_peaks: *mut f32, count: i32) {
    let n = (count as usize).clamp(1, dsp::MAX_SPECTRUM_BANDS);
    let bars = unsafe { std::slice::from_raw_parts_mut(out_bars, n) };
    let peaks = unsafe { std::slice::from_raw_parts_mut(out_peaks, n) };
    if let Ok(dsp) = DSP_PIPELINE.try_lock() {
        if n == 48 {
            bars.copy_from_slice(&dsp.spectrum_bars[..48]);
            peaks.copy_from_slice(&dsp.spectrum_peaks[..48]);
        } else if n == 32 {
            bars.copy_from_slice(&dsp.spectrum_bars[..32]);
            peaks.copy_from_slice(&dsp.spectrum_peaks[..32]);
        } else {
            for i in 0..n {
                let src_idx = (i * 48) / n;
                bars[i] = dsp.spectrum_bars[src_idx];
                peaks[i] = dsp.spectrum_peaks[src_idx];
            }
        }
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_get_position_ms() -> i64 {
    if let Ok(player) = PLAYER_ENGINE.try_lock() {
        player.get_position_ms()
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_get_duration_ms() -> i64 {
    if let Ok(player) = PLAYER_ENGINE.try_lock() {
        player.get_duration_ms()
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_open_file(file_path: *const c_char) -> bool {
    let path_str = unsafe { CStr::from_ptr(file_path).to_string_lossy().into_owned() };
    if let Ok(mut player) = PLAYER_ENGINE.lock() {
        player.open_file(&path_str).is_ok()
    } else {
        false
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_set_dsp_params(delay_enabled: bool, delay_ms: f32, stereo_width: f32) {
    if let Ok(mut dsp) = DSP_PIPELINE.lock() {
        dsp.set_parameters(delay_enabled, delay_ms, stereo_width);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_set_audio_preset(preset_id: i32) {
    if let Ok(mut dsp) = DSP_PIPELINE.lock() {
        let preset = dsp::CarAudioPreset::from_i32(preset_id);
        dsp.apply_preset(preset);
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_update_quota(max_bytes: i64) {
    if let Ok(mut guard) = NAS_MANAGER.lock() {
        if let Some(mgr) = guard.as_mut() {
            mgr.set_max_quota(max_bytes as u64);
            let _ = mgr.evict_early_tracks_if_needed();
        }
    }
}

#[no_mangle]
pub extern "C" fn rust_engine_shutdown() {
    if let Ok(mut player) = PLAYER_ENGINE.lock() {
        player.stop();
    }
    IS_ENGINE_READY.store(false, Ordering::SeqCst);
}

// =========================================================================
// JNI 导出接口：供 Kotlin NativeAudioBridge 直接绑定调用
// =========================================================================

/// 打开并播放本地音频文件（支持 FLAC, MP3, WAV, AAC 等格式，自动关联解析同名 .lrc）
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_playLocalTrack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    song_id: JString<'local>,
    file_path: JString<'local>,
    title: JString<'local>,
    artist: JString<'local>,
) -> jboolean {
    let s_id: String = env.get_string(&song_id).map(|s| s.into()).unwrap_or_default();
    let path: String = env.get_string(&file_path).map(|s| s.into()).unwrap_or_default();
    let s_title: String = env.get_string(&title).map(|s| s.into()).unwrap_or_default();
    let s_artist: String = env.get_string(&artist).map(|s| s.into()).unwrap_or_default();

    if let Ok(mut id_guard) = CURRENT_SONG_ID.lock() {
        *id_guard = s_id.clone();
    }
    if let Ok(mut title_guard) = CURRENT_SONG_TITLE.lock() {
        *title_guard = s_title.clone();
    }
    if let Ok(mut artist_guard) = CURRENT_SONG_ARTIST.lock() {
        *artist_guard = s_artist.clone();
    }

    // 检查是否存在同名同目录下的 .lrc 歌词文件，若存在自动录入数据库
    let lrc_path = Path::new(&path).with_extension("lrc");
    if lrc_path.exists() {
        if let Ok(bytes) = fs::read(&lrc_path) {
            if let Ok(mut db_guard) = LYRICS_DB.lock() {
                if let Some(db) = db_guard.as_mut() {
                    let _ = db.ingest_lrc_bytes(&s_id, &s_title, &s_artist, &bytes);
                }
            }
        }
    }

    let success = if let Ok(mut player) = PLAYER_ENGINE.lock() {
        player.open_file(&path).is_ok()
    } else {
        false
    };

    if success { 1 } else { 0 }
}

/// 获取当前音频总时长（毫秒）
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_getTrackDurationMs<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jlong {
    rust_engine_get_duration_ms()
}

/// 动态调节车载 DSP 参数（可关闭皇帝位延时以兼容原厂丹拿/功放硬件音效）
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_setDspSettings<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    delay_enabled: jboolean,
    delay_ms: jfloat,
    stereo_width: jfloat,
) {
    rust_engine_set_dsp_params(delay_enabled != 0, delay_ms, stereo_width);
}

/// 切换车载音响品类预设（原厂6/8扬声器、Dirac、丹拿Hi-Fi直通、丹拿音乐厅、腾势帝瓦雷、纯净监听）
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_setAudioPreset<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    preset_id: jint,
) {
    rust_engine_set_audio_preset(preset_id);
}

/// 手动导入或更新某首歌的 LRC 歌词文本
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_ingestLyrics<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    song_id: JString<'local>,
    title: JString<'local>,
    artist: JString<'local>,
    lrc_text: JString<'local>,
) -> jboolean {
    let s_id: String = env.get_string(&song_id).map(|s| s.into()).unwrap_or_default();
    let s_title: String = env.get_string(&title).map(|s| s.into()).unwrap_or_default();
    let s_artist: String = env.get_string(&artist).map(|s| s.into()).unwrap_or_default();
    let lrc: String = env.get_string(&lrc_text).map(|s| s.into()).unwrap_or_default();

    if let Ok(mut guard) = LYRICS_DB.lock() {
        if let Some(db) = guard.as_mut() {
            let res = db.ingest_lrc_bytes(&s_id, &s_title, &s_artist, lrc.as_bytes());
            if res.is_ok() {
                return 1;
            }
        }
    }
    0
}

/// 根据当前毫秒进度获取高亮同步歌词行
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_getCurrentLyricLine<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    position_ms: i64,
) -> jstring {
    let current_id = CURRENT_SONG_ID.lock().map(|g| g.clone()).unwrap_or_default();

    let text = if let Ok(guard) = LYRICS_DB.lock() {
        if let Some(db) = guard.as_ref() {
            if !current_id.is_empty() {
                db.get_active_lyric_line(&current_id, position_ms)
                    .unwrap_or_else(|| "千千静听 · 车载 Hi-Fi 音乐".to_string())
            } else {
                "千千静听 · 车载 Hi-Fi 音乐".to_string()
            }
        } else {
            "正在加载曲库...".to_string()
        }
    } else {
        "".to_string()
    };
    env.new_string(text).expect("Failed to create JNI string").into_raw()
}

/// 汉字+全拼双模检索
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_searchAndPlayByLyricsOrTitle<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    spoken_query: JString<'local>,
) -> jstring {
    let query: String = env
        .get_string(&spoken_query)
        .map(|s| s.into())
        .unwrap_or_default();

    let matched_info = if let Ok(guard) = LYRICS_DB.lock() {
        if let Some(db) = guard.as_ref() {
            if let Ok(Some(hit)) = db.search_by_hanzi_or_pinyin(&query) {
                if let Ok(player) = PLAYER_ENGINE.lock() {
                    player.seek_to_ms(hit.time_ms);
                    player.set_playing(true);
                }
                format!("已定位播放：{} - {} ({})", hit.artist, hit.title, hit.lyric_line)
            } else {
                format!("未找到匹配歌词或曲目：{}", query)
            }
        } else {
            "曲库未就绪".to_string()
        }
    } else {
        "检索繁忙".to_string()
    };

    env.new_string(matched_info).expect("JNI string error").into_raw()
}

/// 飞牛 WebDAV 连通性测试 (HTTP PROPFIND 端口 5005/5006 探测)
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_testFeiniuWebdavConnection<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    server_url: JString<'local>,
    username: JString<'local>,
    password: JString<'local>,
    target_dir: JString<'local>,
) -> jstring {
    let url: String = env.get_string(&server_url).map(|s| s.into()).unwrap_or_default();
    let user: String = env.get_string(&username).map(|s| s.into()).unwrap_or_default();
    let pass: String = env.get_string(&password).map(|s| s.into()).unwrap_or_default();
    let dir: String = env.get_string(&target_dir).map(|s| s.into()).unwrap_or_default();

    let result = if let Ok(guard) = NAS_MANAGER.lock() {
        if let Some(mgr) = guard.as_ref() {
            match mgr.test_feiniu_connection(&url, &user, &pass, &dir) {
                Ok(msg) => msg,
                Err(err) => format!("连接失败: {}", err),
            }
        } else {
            "缓存管理器未初始化".to_string()
        }
    } else {
        "系统繁忙，请稍后再试".to_string()
    };

    env.new_string(result).expect("JNI string error").into_raw()
}

/// 保存并配置飞牛 NAS WebDAV
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_configureFeiniuWebdav<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    _server_url: JString<'local>,
    _username: JString<'local>,
    _password: JString<'local>,
    _target_dir: JString<'local>,
    _cache_mode_enabled: jboolean,
    _auto_archive_on_fav: jboolean,
    quota_bytes: jlong,
) {
    rust_engine_update_quota(quota_bytes);
}

/// 触发 WebDAV 远程曲库扫描与同步，返回 JSON 格式曲目清单
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_syncNasLibrary<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    server_url: JString<'local>,
    username: JString<'local>,
    password: JString<'local>,
    target_dir: JString<'local>,
) -> jstring {
    let url: String = env.get_string(&server_url).map(|s| s.into()).unwrap_or_default();
    let user: String = env.get_string(&username).map(|s| s.into()).unwrap_or_default();
    let pass: String = env.get_string(&password).map(|s| s.into()).unwrap_or_default();
    let dir: String = env.get_string(&target_dir).map(|s| s.into()).unwrap_or_default();

    let json = if let Ok(guard) = NAS_MANAGER.lock() {
        if let Some(mgr) = guard.as_ref() {
            match mgr.scan_webdav_tracks(&url, &user, &pass, &dir) {
                Ok(tracks) => {
                    let mut items = Vec::new();
                    for t in tracks {
                        let safe_title = t.title.replace('"', "\\\"");
                        let safe_artist = t.artist.replace('"', "\\\"");
                        let safe_url = t.nas_url.replace('"', "\\\"");
                        items.push(format!(
                            r#"{{"id":"{}","title":"{}","artist":"{}","nasUrl":"{}"}}"#,
                            t.song_id, safe_title, safe_artist, safe_url
                        ));
                    }
                    format!("[{}]", items.join(","))
                }
                Err(_) => "[]".to_string(),
            }
        } else {
            "[]".to_string()
        }
    } else {
        "[]".to_string()
    };

    env.new_string(json).expect("JNI error").into_raw()
}

/// 边下边播飞牛 NAS 音频（支持 512KB 分块断点续传）
#[no_mangle]
pub extern "system" fn Java_com_byd_qianqian_bridge_NativeAudioBridge_streamAndPlayNasTrack<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    song_id: JString<'local>,
    nas_url: JString<'local>,
    username: JString<'local>,
    password: JString<'local>,
    title: JString<'local>,
    artist: JString<'local>,
) -> jboolean {
    let s_id: String = env.get_string(&song_id).map(|s| s.into()).unwrap_or_default();
    let n_url: String = env.get_string(&nas_url).map(|s| s.into()).unwrap_or_default();
    let user: String = env.get_string(&username).map(|s| s.into()).unwrap_or_default();
    let pass: String = env.get_string(&password).map(|s| s.into()).unwrap_or_default();
    let s_title: String = env.get_string(&title).map(|s| s.into()).unwrap_or_default();
    let s_artist: String = env.get_string(&artist).map(|s| s.into()).unwrap_or_default();

    let local_path = if let Ok(guard) = NAS_MANAGER.lock() {
        if let Some(mgr) = guard.as_ref() {
            mgr.stream_and_cache_track(&s_id, &n_url, &user, &pass).ok()
        } else {
            None
        }
    } else {
        None
    };

    if let Some(path_buf) = local_path {
        let path_str = path_buf.to_string_lossy().to_string();
        if let Ok(mut id_guard) = CURRENT_SONG_ID.lock() { *id_guard = s_id; }
        if let Ok(mut title_guard) = CURRENT_SONG_TITLE.lock() { *title_guard = s_title; }
        if let Ok(mut artist_guard) = CURRENT_SONG_ARTIST.lock() { *artist_guard = s_artist; }

        if let Ok(mut player) = PLAYER_ENGINE.lock() {
            if player.open_file(&path_str).is_ok() {
                return 1;
            }
        }
    }
    0
}
