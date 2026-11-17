package com.byd.qianqian

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.service.CarPlaybackService
import com.byd.qianqian.ui.AdaptivePlayerScreen
import com.byd.qianqian.ui.FeiniuNasSettingsSheet
import com.byd.qianqian.ui.FloatingLyricsWindow
import com.byd.qianqian.ui.PlaylistSheet
import com.byd.qianqian.voice.InAppVoiceController

class MainActivity : ComponentActivity() {

    private var isNightModeState by mutableStateOf(true)
    private var floatingLyrics: FloatingLyricsWindow? = null
    private var voiceController: InAppVoiceController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动前台媒体服务（接管小迪播控、方向盘按键与常驻媒体通知）
        startForegroundService(Intent(this, CarPlaybackService::class.java))

        updateNightModeFromCarHeadlights(resources.configuration)
        floatingLyrics = FloatingLyricsWindow(applicationContext)
        voiceController = InAppVoiceController(applicationContext)

        setContent {
            val currentTrack by PlaylistManager.currentTrack.collectAsState()
            val isPlaying by PlaylistManager.isPlaying.collectAsState()
            val currentAudioPreset by PlaylistManager.currentAudioPreset.collectAsState()
            val tracks by PlaylistManager.playlist.collectAsState()

            var showNasSettings by remember { mutableStateOf(false) }
            var showPlaylistSheet by remember { mutableStateOf(false) }
            var showAudioProfileSheet by remember { mutableStateOf(false) }
            var showVoiceWakeSettings by remember { mutableStateOf(false) }

            if (showNasSettings) {
                FeiniuNasSettingsSheet(onClose = { showNasSettings = false })
            } else if (showVoiceWakeSettings) {
                com.byd.qianqian.ui.VoiceWakeSettingsSheet(onClose = { showVoiceWakeSettings = false })
            } else if (showAudioProfileSheet) {
                com.byd.qianqian.ui.AudioProfileSheet(
                    currentPreset = currentAudioPreset,
                    onSelectPreset = { preset ->
                        PlaylistManager.setAudioPreset(preset)
                    },
                    onClose = { showAudioProfileSheet = false }
                )
            } else if (showPlaylistSheet) {
                PlaylistSheet(
                    tracks = tracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    onTrackSelect = { track ->
                        PlaylistManager.playTrack(track, applicationContext)
                        showPlaylistSheet = false
                    },
                    onClose = { showPlaylistSheet = false }
                )
            } else {
                AdaptivePlayerScreen(
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    isNightMode = isNightModeState,
                    currentAudioPreset = currentAudioPreset,
                    onOpenAudioProfile = { showAudioProfileSheet = true },
                    onOpenVoiceSettings = { showVoiceWakeSettings = true },
                    onOpenSettings = { showNasSettings = true },
                    onOpenPlaylist = { showPlaylistSheet = true },
                    onToggleFloatingLyrics = { toggleFloatingLyricsOverlay() },
                    onTriggerInAppVoiceSearch = { voiceController?.startListeningWithDucking() },
                    onPlayPause = { PlaylistManager.togglePlayPause() },
                    onPrev = { PlaylistManager.skipPrevious() },
                    onNext = { PlaylistManager.skipNext() }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 响应比亚迪中控屏横竖屏旋转、分屏拖拽或大灯开启切换昼夜模式
        updateNightModeFromCarHeadlights(newConfig)
    }

    private fun updateNightModeFromCarHeadlights(config: Configuration) {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val nightMask = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isNightModeState = (nightMask == Configuration.UI_MODE_NIGHT_YES) ||
                (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES)
    }

    private fun toggleFloatingLyricsOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }
        floatingLyrics?.toggle()
    }

    override fun onDestroy() {
        voiceController?.release()
        super.onDestroy()
    }
}
