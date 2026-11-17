package com.byd.qianqian.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.manager.VoiceWakeManager
import kotlinx.coroutines.*
import kotlin.math.abs

/**
 * 车规端侧离线语音与自定义唤醒控制器：
 * 1. 监测环境录音，匹配用户自定义唤醒词（“你好千千”或车主录制声纹模板）
 * 2. 唤醒触发瞬间调用 NativeAudioBridge.setDuckingGain(0.15f) 将音乐压低至 15%，消除车厢喇叭回声
 * 3. 收集指令后路由至 PlaylistManager 切歌/暂停，或 Rust SQLite FTS5 汉字+拼音容错检索
 */
class InAppVoiceController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ambientWakeJob: Job? = null
    private var commandListeningJob: Job? = null

    init {
        VoiceWakeManager.init(context)
        startAmbientWakeListening()
    }

    /** 低功耗后台环境监听，监测自定义唤醒词或录制的车主声学模板 */
    @SuppressLint("MissingPermission")
    private fun startAmbientWakeListening() {
        if (ambientWakeJob?.isActive == true) return

        ambientWakeJob = scope.launch {
            val sampleRate = VoiceWakeManager.SAMPLE_RATE
            val bufferSize = sampleRate * 16 / 10 // 1.6 秒音频缓存

            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                recorder.startRecording()

                val rollingBuffer = ShortArray(bufferSize)
                val chunk = ShortArray(1600) // 100ms 一帧

                while (isActive) {
                    if (!VoiceWakeManager.config.value.enabled) {
                        delay(500)
                        continue
                    }

                    val count = recorder.read(chunk, 0, chunk.size)
                    if (count > 0) {
                        // 环形推进滑动窗口
                        System.arraycopy(rollingBuffer, count, rollingBuffer, 0, bufferSize - count)
                        System.arraycopy(chunk, 0, rollingBuffer, bufferSize - count, count)

                        // 粗筛人声能量，避免静音期无谓计算
                        var energySum = 0L
                        for (s in chunk) energySum += abs(s.toInt())
                        if (energySum / chunk.size > 800) {
                            if (VoiceWakeManager.matchLiveAudio(rollingBuffer)) {
                                // 唤醒成功！立即进入指令倾听
                                withContext(Dispatchers.Main) {
                                    startListeningWithDucking()
                                }
                                delay(4000) // 防连续重复唤醒
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListeningWithDucking() {
        if (commandListeningJob?.isActive == true) return

        // 1. 立即压低正在播放的音乐至 15%，防止扬声器声音灌入麦克风
        NativeAudioBridge.setDuckingGain(0.15f, rampMs = 80)

        commandListeningJob = scope.launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            var recorder: AudioRecord? = null

            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                recorder.startRecording()
                val pcmBuf = ShortArray(1600) // 100ms
                var elapsedMs = 0
                var speechDetected = false

                while (isActive && elapsedMs < 3500) {
                    val read = recorder.read(pcmBuf, 0, pcmBuf.size)
                    if (read > 0) {
                        var energy = 0L
                        for (s in pcmBuf) energy += abs(s.toInt())
                        if (energy / read > 1200) {
                            speechDetected = true
                        }
                        elapsedMs += 100
                    }
                }
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
                // 恢复 100% 音乐音量
                NativeAudioBridge.setDuckingGain(1.0f, rampMs = 250)
            }
        }
    }

    /** 意图路由处理：支持播控命令与歌词/曲名双模检索 */
    fun onAsrTextRecognized(text: String) {
        val clean = text.trim()
        val currentWakeWord = VoiceWakeManager.config.value.wakeWordText

        // 剥离唤醒词前缀（你好千千 / 小迪等）
        val stripped = clean
            .replace(Regex("^(你好小迪|小迪|${Regex.escape(currentWakeWord)}|我想听|播放|搜歌词|搜索歌词|找一下|唱一句)"), "")
            .trim()

        when {
            clean.contains("暂停") || clean.contains("停止") -> PlaylistManager.togglePlayPause()
            clean.contains("继续") || clean == "播放" -> {
                if (!PlaylistManager.isPlaying.value) PlaylistManager.togglePlayPause()
            }
            clean.contains("下一首") || clean.contains("切歌") -> PlaylistManager.skipNext()
            clean.contains("上一首") -> PlaylistManager.skipPrevious()
            stripped.isNotEmpty() -> {
                NativeAudioBridge.searchAndPlayByLyricsOrTitle(stripped)
            }
        }
    }

    fun release() {
        ambientWakeJob?.cancel()
        commandListeningJob?.cancel()
        scope.cancel()
    }
}
