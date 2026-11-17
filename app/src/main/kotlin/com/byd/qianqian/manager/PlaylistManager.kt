package com.byd.qianqian.manager

import android.content.Context
import android.os.Environment
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object PlaylistManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentAudioPreset = MutableStateFlow(com.byd.qianqian.model.AudioPreset.BYD_STANDARD)
    val currentAudioPreset = _currentAudioPreset.asStateFlow()

    private val _driverDelayEnabled = MutableStateFlow(true)
    val driverDelayEnabled = _driverDelayEnabled.asStateFlow()

    private val _stereoWidth = MutableStateFlow(1.35f)
    val stereoWidth = _stereoWidth.asStateFlow()

    var onTrackChangedListener: ((Track, Boolean) -> Unit)? = null

    init {
        // 初始填充经典车载演示曲目
        initDefaultDemoTracks()
    }

    private fun initDefaultDemoTracks() {
        val demoList = listOf(
            Track(
                id = "jay_qiantian",
                title = "晴天",
                artist = "周杰伦",
                album = "叶惠美",
                path = "",
                durationMs = 269_000L,
                isNas = true
            ),
            Track(
                id = "beyond_haikuotiankong",
                title = "海阔天空",
                artist = "Beyond",
                album = "乐与怒",
                path = "",
                durationMs = 324_000L,
                isNas = true
            ),
            Track(
                id = "faye_hongdou",
                title = "红豆",
                artist = "王菲",
                album = "唱游",
                path = "",
                durationMs = 255_000L,
                isNas = true
            ),
            Track(
                id = "eason_shi_nian",
                title = "十年",
                artist = "陈奕迅",
                album = "黑·白·灰",
                path = "",
                durationMs = 205_000L,
                isNas = true
            )
        )
        _playlist.value = demoList
        _currentTrack.value = demoList.first()
    }

    /** 扫描车机本地存储及插入的 FAT32/exFAT U盘中的音乐与同名 LRC 歌词 */
    fun scanLocalAndUsbStorage(context: Context) {
        scope.launch(Dispatchers.IO) {
            val foundTracks = mutableListOf<Track>()
            val scanDirs = mutableListOf<File>()

            // 1. 标准车机音乐目录
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (musicDir.exists()) scanDirs.add(musicDir)

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir.exists()) scanDirs.add(downloadDir)

            // 2. 比亚迪宋 USB 接口挂载点 (/storage/xxxx-xxxx)
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                storageRoot.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name != "emulated" && file.name != "self") {
                        scanDirs.add(file)
                    }
                }
            }

            // 3. 应用内部 NAS 缓存目录
            val nasCacheDir = context.filesDir.resolve("nas_cache")
            if (nasCacheDir.exists()) scanDirs.add(nasCacheDir)

            val audioExtensions = setOf("flac", "mp3", "wav", "m4a", "aac", "ogg", "ape")

            scanDirs.forEach { dir ->
                dir.walkTopDown().maxDepth(4).forEach { file ->
                    if (file.isFile && audioExtensions.contains(file.extension.lowercase())) {
                        val baseName = file.nameWithoutExtension
                        val parts = baseName.split("-", "_").map { it.trim() }
                        val (artist, title) = if (parts.size >= 2) {
                            parts[0] to parts.subList(1, parts.size).joinToString(" - ")
                        } else {
                            "车载本地" to baseName
                        }

                        val track = Track(
                            id = file.absolutePath.hashCode().toString(),
                            title = title,
                            artist = artist,
                            album = "车载存储",
                            path = file.absolutePath,
                            durationMs = 0L,
                            isNas = file.absolutePath.contains("nas_cache")
                        )
                        foundTracks.add(track)

                        // 若存在同名 LRC 歌词，自动注入本地数据库
                        val lrcFile = File(file.parentFile, "$baseName.lrc")
                        if (lrcFile.exists()) {
                            try {
                                val lrcContent = lrcFile.readText()
                                NativeAudioBridge.ingestLyrics(track.id, track.title, track.artist, lrcContent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            if (foundTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _playlist.value = foundTracks
                    if (_currentTrack.value == null || _currentTrack.value?.path.isNullOrEmpty()) {
                        _currentTrack.value = foundTracks.first()
                        _currentIndex.value = 0
                    }
                }
            }
        }
    }

    fun playTrack(track: Track, context: Context? = null) {
        val idx = _playlist.value.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            _currentIndex.value = idx
        }
        _currentTrack.value = track

        if (track.path.isNotEmpty() && File(track.path).exists()) {
            val ok = NativeAudioBridge.playLocalTrack(
                songId = track.id,
                filePath = track.path,
                title = track.title,
                artist = track.artist
            )
            _isPlaying.value = ok
            NativeAudioBridge.setPlaybackState(ok)
        } else if (track.isNas && context != null) {
            // 异步分块拉取并播放飞牛 NAS 音频
            scope.launch(Dispatchers.IO) {
                val nasConfig = NasSettingsManager.load(context)
                val ok = NativeAudioBridge.streamAndPlayNasTrack(
                    songId = track.id,
                    nasUrl = track.path,
                    username = nasConfig.username,
                    password = nasConfig.password,
                    title = track.title,
                    artist = track.artist
                )
                withContext(Dispatchers.Main) {
                    _isPlaying.value = ok
                    NativeAudioBridge.setPlaybackState(ok)
                    onTrackChangedListener?.invoke(track, ok)
                }
            }
            return
        } else {
            // 演示曲目或无本地路径时
            _isPlaying.value = true
            NativeAudioBridge.setPlaybackState(true)
        }

        onTrackChangedListener?.invoke(track, _isPlaying.value)
    }

    /** 导入飞牛 WebDAV 扫描发现的远程曲目 */
    fun importNasTracksFromJson(json: String) {
        if (json.isEmpty() || json == "[]") return
        scope.launch(Dispatchers.Default) {
            try {
                val jsonArray = org.json.JSONArray(json)
                val nasTracks = mutableListOf<Track>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    nasTracks.add(
                        Track(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            artist = obj.getString("artist"),
                            album = "飞牛 NAS",
                            path = obj.getString("nasUrl"),
                            isNas = true
                        )
                    )
                }
                if (nasTracks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val merged = (_playlist.value.filter { !it.isNas } + nasTracks).distinctBy { it.id }
                        _playlist.value = merged
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun playAtIndex(index: Int) {
        val list = _playlist.value
        if (list.isEmpty()) return
        val targetIdx = index.coerceIn(0, list.size - 1)
        _currentIndex.value = targetIdx
        playTrack(list[targetIdx])
    }

    /**
     * 比亚迪小迪语音与端侧语音核心：智能解析自然语言，匹配歌名/歌手并触发播放
     * 支持「周杰伦的晴天」、「播放海阔天空」、「我想听王菲」、「播放音乐」
     */
    fun searchAndPlay(query: String, context: Context? = null): Boolean {
        val clean = query.trim()
            .replace(Regex("^(你好小迪|小迪|你好千千|千千|我想听|播放|放一首|来一首|放首歌|搜歌词|搜索歌词|找一下|唱一句|唱首|放)"), "")
            .replace(Regex("(这首歌|这首音乐|的歌)$"), "")
            .trim()

        if (clean.isEmpty()) {
            if (!_isPlaying.value) {
                togglePlayPause()
            }
            return true
        }

        val list = _playlist.value
        val splitQuery = if (clean.contains("的")) {
            val parts = clean.split("的", limit = 2)
            parts[0].trim() to parts[1].trim()
        } else {
            null
        }

        val matchedTrack = list.firstOrNull { track ->
            if (splitQuery != null) {
                val (artistPart, titlePart) = splitQuery
                (track.artist.contains(artistPart, ignoreCase = true) && track.title.contains(titlePart, ignoreCase = true)) ||
                (track.title.contains(clean, ignoreCase = true) || track.artist.contains(clean, ignoreCase = true))
            } else {
                track.title.contains(clean, ignoreCase = true) ||
                track.artist.contains(clean, ignoreCase = true) ||
                clean.contains(track.title, ignoreCase = true)
            }
        }

        if (matchedTrack != null) {
            playTrack(matchedTrack, context)
            return true
        }

        // 调用底座 Rust SQLite FTS5 引擎进行拼音与全歌词检索
        val hitMsg = NativeAudioBridge.searchAndPlayByLyricsOrTitle(clean)
        if (hitMsg.contains("已定位播放")) {
            _isPlaying.value = true
            return true
        }
        return false
    }

    fun togglePlayPause() {
        val nextState = !_isPlaying.value
        _isPlaying.value = nextState
        NativeAudioBridge.setPlaybackState(nextState)
        _currentTrack.value?.let { onTrackChangedListener?.invoke(it, nextState) }
    }

    fun skipNext() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val nextIdx = (_currentIndex.value + 1) % list.size
        playAtIndex(nextIdx)
    }

    fun skipPrevious() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val prevIdx = if (_currentIndex.value - 1 < 0) list.size - 1 else _currentIndex.value - 1
        playAtIndex(prevIdx)
    }

    fun seekTo(positionMs: Long) {
        NativeAudioBridge.seekToMs(positionMs)
    }

    fun setDspParameters(delayEnabled: Boolean, stereoWidth: Float) {
        _driverDelayEnabled.value = delayEnabled
        _stereoWidth.value = stereoWidth
        NativeAudioBridge.setDspSettings(
            delayEnabled = delayEnabled,
            delayMs = 1.8f,
            stereoWidth = stereoWidth
        )
    }

    fun setAudioPreset(preset: com.byd.qianqian.model.AudioPreset) {
        _currentAudioPreset.value = preset
        NativeAudioBridge.setAudioPreset(preset.id)
    }
}
