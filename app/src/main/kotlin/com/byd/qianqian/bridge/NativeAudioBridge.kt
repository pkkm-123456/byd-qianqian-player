package com.byd.qianqian.bridge

/**
 * Kotlin 与底层 Rust (libqianqian_core.so) + C++ (libcar_audio_jni.so) 的零拷贝桥接层
 */
object NativeAudioBridge {
    init {
        System.loadLibrary("qianqian_core")
        System.loadLibrary("car_audio_jni")
    }

    /** 初始化音频引擎、主驾皇帝位延时、立体声宽度与 NAS 缓存目录配额 */
    external fun initEngine(
        sampleRate: Int,
        driverDelayMs: Float,
        stereoWidth: Float,
        cacheDir: String,
        dbPath: String,
        maxCacheBytes: Long
    )

    /** 启动或暂停 Oboe + Rust 播放流 */
    external fun setPlaybackState(playing: Boolean)

    /** 毫秒级精准定位 */
    external fun seekToMs(positionMs: Long)

    /** 切换下一首 / 上一首 */
    external fun skipNext()
    external fun skipPrevious()

    /** 设置高德导航播报或语音收音时的平滑压低音量 (Ducking: 0.2f = 20%, 1.0f = 100%) */
    external fun setDuckingGain(targetGain: Float, rampMs: Int)

    /** 60fps 零分配拉取 32 段千千静听频谱高度与顶部峰值帽高度 (0.0 ~ 1.0) */
    external fun pullSpectrum32(outBars32: FloatArray, outPeaks32: FloatArray)

    /** 动态拉取指定数组长度（例如 48/64 段）千千静听高密度频谱高度与顶部峰值帽高度 */
    external fun pullSpectrum(outBars: FloatArray, outPeaks: FloatArray)

    /** 获取当前播放毫秒进度 */
    external fun getCurrentPositionMs(): Long

    /** 根据当前毫秒进度从 Rust 引擎获取同步高亮的 LRC 歌词行 */
    external fun getCurrentLyricLine(positionMs: Long): String

    /** 语音或文本搜索：同时匹配歌名、歌手、歌词正文及无声调拼音索引，命中后直接播放 */
    external fun searchAndPlayByLyricsOrTitle(spokenQuery: String): String

    /** 配置 NAS WebDAV 地址与账号并触发后台 64KB 头部/歌词扫描与 LRU 水位线清理，返回曲目列表 JSON */
    external fun syncNasLibrary(
        serverUrl: String,
        username: String,
        password: String,
        targetDir: String
    ): String

    /** 边下边播飞牛 NAS 音频（支持 512KB 分块断点续传） */
    external fun streamAndPlayNasTrack(
        songId: String,
        nasUrl: String,
        username: String,
        password: String,
        title: String,
        artist: String
    ): Boolean

    /** 动态更新 NAS 本地缓存最大配额（字节），超过时自动淘汰早期播放歌曲至 85% 水位线 */
    external fun updateCacheQuotaBytes(maxBytes: Long)

    /** 关闭引擎并持久化最后播放进度 */
    external fun shutdownEngine()

    /** 测试飞牛 fnOS WebDAV (端口 5005/5006) 连通性并校验共享目录权限 */
    external fun testFeiniuWebdavConnection(
        serverUrl: String,
        username: String,
        password: String,
        targetDir: String
    ): String

    /** 保存并启用飞牛 WebDAV 直存配置（支持在线播放/下载缓存切换与收藏自动下载归档） */
    external fun configureFeiniuWebdav(
        serverUrl: String,
        username: String,
        password: String,
        targetDir: String,
        cacheModeEnabled: Boolean,
        autoArchiveOnFav: Boolean,
        quotaBytes: Long
    )

    /** 打开并播放本地音频文件（支持 FLAC, MP3, WAV, AAC 等，自动关联同名 .lrc） */
    external fun playLocalTrack(
        songId: String,
        filePath: String,
        title: String,
        artist: String
    ): Boolean

    /** 获取当前播放曲目的总时长（毫秒） */
    external fun getTrackDurationMs(): Long

    /** 动态设置车载 DSP 参数（可关闭皇帝位延时以兼容原车丹拿/功放硬件音效） */
    external fun setDspSettings(
        delayEnabled: Boolean,
        delayMs: Float,
        stereoWidth: Float
    )

    /** 手动录入或导入歌词（LRC 格式） */
    external fun ingestLyrics(
        songId: String,
        title: String,
        artist: String,
        lrcText: String
    ): Boolean

    /** 切换比亚迪音响品类预设 (0:原厂标准6/8, 1:Dirac, 2:丹拿Hi-Fi直通, 3:丹拿音乐厅, 4:腾势帝瓦雷, 5:纯净直通) */
    external fun setAudioPreset(presetId: Int)
}
