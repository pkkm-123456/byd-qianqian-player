package com.byd.qianqian.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.byd.qianqian.MainActivity
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.model.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*

class CarPlaybackService : MediaSessionService(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val CHANNEL_ID = "qianqian_car_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.byd.qianqian.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.byd.qianqian.ACTION_PREV"
        const val ACTION_NEXT = "com.byd.qianqian.ACTION_NEXT"
        const val ACTION_BOOT_RESUME = "com.byd.qianqian.ACTION_BOOT_RESUME"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var basePlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest
    private var resumeOnFocusGain = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoSaveJob: Job? = null

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
            }
        }
    }

    private val usbDynamicReceiver = UsbStorageReceiver()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val cacheDir = filesDir.resolve("nas_cache").apply { mkdirs() }.absolutePath
        val dbPath = filesDir.resolve("qianqian_car.db").absolutePath

        // 1. 初始化底层 Rust 核心 DSP、SQLite 拼音词库与 NAS 缓存
        NativeAudioBridge.initEngine(
            sampleRate = 48000,
            driverDelayMs = 1.8f,
            stereoWidth = 1.35f,
            cacheDir = cacheDir,
            dbPath = dbPath,
            maxCacheBytes = 4L * 1024L * 1024L * 1024L // 4GB 缓存配额
        )

        // 2. 创建 Android 8.0+ / 14+ 车载前台服务常驻通知通道，防止系统杀后台
        createNotificationChannel()
        val initialNotif = buildNotification(PlaylistManager.currentTrack.value, isPlaying = false)
        startForeground(NOTIFICATION_ID, initialNotif)

        // 3. 配置车载 AudioFocus（高德地图导航、倒车雷达混音）
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(this)
            .build()

        // 4. 构建 Media3 ExoPlayer 与 ForwardingPlayer，用于完全同步比亚迪方向盘按键与仪表盘信息
        basePlayer = ExoPlayer.Builder(this).build()
        val carPlayer = object : ForwardingPlayer(basePlayer) {
            override fun play() {
                if (requestCarAudioFocus()) {
                    if (!PlaylistManager.isPlaying.value) {
                        PlaylistManager.togglePlayPause()
                    }
                    super.play()
                }
            }

            override fun pause() {
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
                super.pause()
            }

            override fun seekTo(positionMs: Long) {
                PlaylistManager.seekTo(positionMs)
                super.seekTo(positionMs)
            }

            override fun seekToNext() {
                PlaylistManager.skipNext()
            }

            override fun seekToPrevious() {
                PlaylistManager.skipPrevious()
            }
        }

        mediaSession = MediaSession.Builder(this, carPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_VOICE_LYRICS_SEARCH") {
                        val query = args.getString("query").orEmpty()
                        PlaylistManager.searchAndPlay(query, applicationContext)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val track = PlaylistManager.currentTrack.value
                    if (track != null) {
                        val metadata = MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()
                        val item = MediaItem.Builder()
                            .setMediaId(track.id)
                            .setMediaMetadata(metadata)
                            .build()
                        return Futures.immediateFuture(
                            MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L)
                        )
                    }
                    return super.onPlaybackResumption(mediaSession, controller)
                }

                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                PlaylistManager.skipNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                PlaylistManager.skipPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                PlaylistManager.togglePlayPause()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()

        // 5. 监听播放列表与曲目切换，联动车机仪表盘元数据与前台通知
        PlaylistManager.onTrackChangedListener = { track, isPlaying ->
            syncTrackToMediaSessionAndNotification(track, isPlaying)
        }

        // 6. 注册蓝牙/输出通道防爆音监听与 USB U盘热插拔动态监听
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        val usbFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        registerReceiver(usbDynamicReceiver, usbFilter)

        // 7. 恢复熄火前播放状态并异步扫描车机存储与 U 盘中的音乐
        PlaylistManager.restorePlaybackState(applicationContext)
        PlaylistManager.scanLocalAndUsbStorage(applicationContext)

        // 8. 每 5 秒持久化当前毫秒进度，防止车辆突然断电丢失进度
        autoSaveJob = serviceScope.launch {
            while (isActive) {
                delay(5000L)
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.savePlaybackState(applicationContext)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> PlaylistManager.togglePlayPause()
            ACTION_PREV -> PlaylistManager.skipPrevious()
            ACTION_NEXT -> PlaylistManager.skipNext()
            ACTION_BOOT_RESUME -> {
                PlaylistManager.currentTrack.value?.let { track ->
                    if (!PlaylistManager.isPlaying.value) {
                        PlaylistManager.playTrack(track, applicationContext)
                    }
                }
            }
            "android.media.action.MEDIA_PLAY_FROM_SEARCH" -> {
                val q = intent.getStringExtra(android.app.SearchManager.QUERY).orEmpty()
                PlaylistManager.searchAndPlay(q, applicationContext)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun syncTrackToMediaSessionAndNotification(track: Track, isPlaying: Boolean) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .build()

        basePlayer.setMediaItem(mediaItem)
        basePlayer.prepare()

        if (isPlaying) {
            requestCarAudioFocus()
            basePlayer.play()
        } else {
            basePlayer.pause()
        }

        val updatedNotif = buildNotification(track, isPlaying)
        val notifMgr = getSystemService(NotificationManager::class.java)
        notifMgr.notify(NOTIFICATION_ID, updatedNotif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "千千静听车载播放服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台音频播放与方向盘按键响应"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(track: Track?, isPlaying: Boolean): Notification {
        val title = track?.title ?: "千千静听 · 车载 Hi-Fi"
        val artist = track?.artist ?: "车载无损音乐"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CarPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CarPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, CarPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "暂停" else "播放"

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "上一首", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "下一首", nextIntent)
            .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun requestCarAudioFocus(): Boolean {
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                NativeAudioBridge.setDuckingGain(1.0f, rampMs = 300)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    PlaylistManager.togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 高德导航播报：平滑压低至 20%
                NativeAudioBridge.setDuckingGain(0.20f, rampMs = 120)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 呼叫小迪、挂倒挡看360全景影像或蓝牙通话：暂停播放
                resumeOnFocusGain = PlaylistManager.isPlaying.value
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        autoSaveJob?.cancel()
        serviceScope.cancel()
        PlaylistManager.savePlaybackState(applicationContext)
        try { unregisterReceiver(becomingNoisyReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(usbDynamicReceiver) } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
        mediaSession?.run {
            player.release()
            release()
        }
        NativeAudioBridge.shutdownEngine()
        super.onDestroy()
    }
}
