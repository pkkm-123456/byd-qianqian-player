package com.byd.qianqian.manager

import android.content.Context
import android.content.SharedPreferences
import com.byd.qianqian.model.VoiceWakeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

object VoiceWakeManager {
    private const val PREF_NAME = "qianqian_voice_wake_prefs"
    private const val TEMPLATE_FILE = "wake_template_mfcc.bin"
    const val SAMPLE_RATE = 16000
    const val RECORD_DURATION_MS = 1600 // 录制窗口 1.6 秒
    const val NUM_FRAMES = 32           // 划分为 32 个时间窗

    private val _config = MutableStateFlow(VoiceWakeConfig())
    val config = _config.asStateFlow()

    private var activeTemplate: FloatArray? = null
    private val recordedTakesBuffer = mutableListOf<FloatArray>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val enabled = prefs.getBoolean("wake_enabled", true)
        val text = prefs.getString("wake_text", "你好千千") ?: "你好千千"
        val sensitivity = prefs.getFloat("wake_sensitivity", 0.65f)

        val templateFile = File(context.filesDir, TEMPLATE_FILE)
        val hasTemplate = templateFile.exists() && templateFile.length() > 0
        if (hasTemplate) {
            loadTemplate(templateFile)
        }

        _config.value = VoiceWakeConfig(
            enabled = enabled,
            wakeWordText = text,
            sensitivity = sensitivity,
            hasCustomVoiceTemplate = hasTemplate,
            recordedTakes = if (hasTemplate) 3 else 0
        )
    }

    fun updateWakeWordText(context: Context, newText: String) {
        val clean = newText.trim().ifEmpty { "你好千千" }
        _config.value = _config.value.copy(wakeWordText = clean)
        getPrefs(context).edit().putString("wake_text", clean).apply()
    }

    fun setWakeEnabled(context: Context, enabled: Boolean) {
        _config.value = _config.value.copy(enabled = enabled)
        getPrefs(context).edit().putBoolean("wake_enabled", enabled).apply()
    }

    fun setSensitivity(context: Context, sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.2f, 0.95f)
        _config.value = _config.value.copy(sensitivity = clamped)
        getPrefs(context).edit().putFloat("wake_sensitivity", clamped).apply()
    }

    /** 处理车主录制的一遍唤醒语音采样，提取时频声学能量包络特征 */
    fun ingestVoiceTake(context: Context, takeIndex: Int, pcmSamples: ShortArray): Boolean {
        if (pcmSamples.size < SAMPLE_RATE) return false
        val features = extractAcousticFeatures(pcmSamples)

        if (takeIndex == 0) {
            recordedTakesBuffer.clear()
        }
        recordedTakesBuffer.add(features)

        _config.value = _config.value.copy(recordedTakes = recordedTakesBuffer.size)

        // 录满 3 遍后生成多样本均值声学模板并保存
        if (recordedTakesBuffer.size >= 3) {
            val averaged = FloatArray(NUM_FRAMES * 2)
            for (feat in recordedTakesBuffer) {
                for (i in feat.indices) {
                    averaged[i] += feat[i] / recordedTakesBuffer.size.toFloat()
                }
            }
            activeTemplate = averaged
            saveTemplate(File(context.filesDir, TEMPLATE_FILE), averaged)
            _config.value = _config.value.copy(hasCustomVoiceTemplate = true)
            return true
        }
        return false
    }

    fun clearCustomVoiceTemplate(context: Context) {
        recordedTakesBuffer.clear()
        activeTemplate = null
        val file = File(context.filesDir, TEMPLATE_FILE)
        if (file.exists()) file.delete()
        _config.value = _config.value.copy(
            hasCustomVoiceTemplate = false,
            recordedTakes = 0
        )
    }

    /** 实时麦克风音频流声学匹配（返回匹置信度是否超过设定的灵敏度阈值） */
    fun matchLiveAudio(pcmBuffer: ShortArray): Boolean {
        if (!_config.value.enabled) return false
        val template = activeTemplate ?: return false

        val liveFeatures = extractAcousticFeatures(pcmBuffer)
        val correlation = computeCosineSimilarity(liveFeatures, template)

        // 灵敏度越高，所需相似度门槛越低 (0.65 灵敏度对应约 0.72 相似度门限)
        val threshold = 0.92f - (_config.value.sensitivity * 0.32f)
        return correlation >= threshold
    }

    private fun extractAcousticFeatures(samples: ShortArray): FloatArray {
        val features = FloatArray(NUM_FRAMES * 2)
        val frameSize = (samples.size / NUM_FRAMES).coerceAtLeast(1)

        var maxEnergy = 1e-4f
        for (f in 0 until NUM_FRAMES) {
            val start = f * frameSize
            val end = (start + frameSize).coerceAtMost(samples.size)

            var sumSq = 0.0
            var zcr = 0
            for (i in start until end - 1) {
                val s = samples[i].toFloat() / 32768f
                sumSq += s * s
                if ((samples[i] >= 0 && samples[i + 1] < 0) || (samples[i] < 0 && samples[i + 1] >= 0)) {
                    zcr++
                }
            }
            val rms = sqrt(sumSq / (end - start).coerceAtLeast(1)).toFloat()
            if (rms > maxEnergy) maxEnergy = rms

            features[f] = rms
            features[NUM_FRAMES + f] = zcr.toFloat() / frameSize.toFloat()
        }

        // 归一化能量包络
        for (f in 0 until NUM_FRAMES) {
            features[f] = (features[f] / maxEnergy).coerceIn(0f, 1f)
        }
        return features
    }

    private fun computeCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    private fun saveTemplate(file: File, template: FloatArray) {
        try {
            file.outputStream().use { fos ->
                for (v in template) {
                    val bits = java.lang.Float.floatToIntBits(v)
                    fos.write(bits and 0xFF)
                    fos.write((bits shr 8) and 0xFF)
                    fos.write((bits shr 16) and 0xFF)
                    fos.write((bits shr 24) and 0xFF)
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadTemplate(file: File) {
        try {
            val bytes = file.readBytes()
            val floats = FloatArray(bytes.size / 4)
            for (i in floats.indices) {
                val idx = i * 4
                val bits = (bytes[idx].toInt() and 0xFF) or
                        ((bytes[idx + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[idx + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[idx + 3].toInt() and 0xFF) shl 24)
                floats[i] = java.lang.Float.intBitsToFloat(bits)
            }
            activeTemplate = floats
        } catch (_: Exception) {}
    }
}
