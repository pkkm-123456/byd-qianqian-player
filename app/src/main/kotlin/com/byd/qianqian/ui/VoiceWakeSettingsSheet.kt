package com.byd.qianqian.ui

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.qianqian.manager.VoiceWakeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音唤醒词设置与车主专属声学特征录制向导：
 * 1. 支持自定义唤醒文本（默认“你好千千”，可自由修改为“千千同学”、“小迪听歌”、“播放音乐”等）
 * 2. 支持连续录制 3 遍车主声音，提取时频包络生成本地离线专属声学模板
 * 3. 动态调节灵敏度，兼顾防车厢胎噪误唤醒与低声速唤
 * 4. 完美融合比亚迪“你好小迪”系统级播控说明
 */
@Composable
fun VoiceWakeSettingsSheet(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val wakeConfig by VoiceWakeManager.config.collectAsState()

    var wakeText by remember(wakeConfig.wakeWordText) { mutableStateOf(wakeConfig.wakeWordText) }
    var sensitivity by remember(wakeConfig.sensitivity) { mutableFloatStateOf(wakeConfig.sensitivity) }
    var isRecordingTake by remember { mutableStateOf(false) }
    var currentTakeIndex by remember { mutableIntStateOf(wakeConfig.recordedTakes) }
    var recordingFeedbackMsg by remember { mutableStateOf("") }

    val greenAccent = Color(0xFF00E676)
    val cardBg = Color(0xFF141A24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 顶部标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<  语音唤醒词与控制设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clickable { onClose() }
            )
            Switch(
                checked = wakeConfig.enabled,
                onCheckedChange = { VoiceWakeManager.setWakeEnabled(context, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
            )
        }

        Text(
            text = "在车内说出唤醒词自动压低音量，倾听「下一首 / 暂停 / 我想听周杰伦」等指令",
            fontSize = 12.sp,
            color = Color(0xFF90A4AE)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. 自定义唤醒词文本卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text("自定义唤醒词文本", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text("建议设定为 3~5 个汉字（如：你好千千、千千同学、播放音乐）", fontSize = 12.sp, color = Color(0xFF78909C))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = wakeText,
                    onValueChange = { wakeText = it.trim() },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = greenAccent,
                        unfocusedBorderColor = Color(0xFF2C3E50)
                    ),
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(greenAccent, RoundedCornerShape(8.dp))
                        .clickable {
                            VoiceWakeManager.updateWakeWordText(context, wakeText)
                            recordingFeedbackMsg = "唤醒词已保存为「$wakeText」"
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("保存", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. 车主专属声纹录制向导 (录音录制功能)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("车主声学特征录制 (3遍校准)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (wakeConfig.hasCustomVoiceTemplate) {
                    Text("● 已就绪", color = greenAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "连续录制 3 遍你口述的唤醒词，可在胎噪背景下精准识别你的音色特征，防他人误唤醒。",
                fontSize = 12.sp,
                color = Color(0xFF78909C)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 进度指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0..2) {
                    val isDone = i < currentTakeIndex
                    val isCurrent = i == currentTakeIndex && isRecordingTake
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(
                                when {
                                    isDone -> greenAccent
                                    isCurrent -> Color(0xFFFFB74D)
                                    else -> Color(0xFF263238)
                                },
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!wakeConfig.hasCustomVoiceTemplate || currentTakeIndex < 3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isRecordingTake) Color(0xFFE65100) else Color(0xFF1E2E25),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isRecordingTake) Color(0xFFFF9800) else greenAccent, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isRecordingTake) {
                            isRecordingTake = true
                            recordingFeedbackMsg = "正在收音，请清晰说出：「$wakeText」..."
                            coroutineScope.launch(Dispatchers.IO) {
                                val samples = recordAudioSample()
                                val finished = VoiceWakeManager.ingestVoiceTake(context, currentTakeIndex, samples)
                                withContext(Dispatchers.Main) {
                                    isRecordingTake = false
                                    if (finished) {
                                        currentTakeIndex = 3
                                        recordingFeedbackMsg = "🎉 3遍录制完成！车主声学模板已成功建立并生效！"
                                    } else {
                                        currentTakeIndex++
                                        recordingFeedbackMsg = "第 $currentTakeIndex 遍录制成功，请点击继续录制下一遍。"
                                    }
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRecordingTake) "🎙️ 正在收音中 (约1.6秒)..." else "🎙️ 开始录制第 ${currentTakeIndex + 1} / 3 遍",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                            .clickable {
                                currentTakeIndex = 0
                                VoiceWakeManager.clearCustomVoiceTemplate(context)
                                recordingFeedbackMsg = "已重置，请点击录制第 1 遍"
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("重新录制声纹", color = Color.LightGray, fontSize = 14.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF37474F), RoundedCornerShape(8.dp))
                            .clickable {
                                VoiceWakeManager.clearCustomVoiceTemplate(context)
                                recordingFeedbackMsg = "已清除个性化声纹，恢复标准文本匹配模式"
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("恢复默认模型", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (recordingFeedbackMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recordingFeedbackMsg,
                    color = if (recordingFeedbackMsg.contains("成功") || recordingFeedbackMsg.contains("完成")) greenAccent else Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 灵敏度滑块
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("唤醒灵敏度调节", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = when {
                        sensitivity < 0.45f -> "低 (抗风噪误唤醒)"
                        sensitivity > 0.80f -> "极高 (轻语微声速唤)"
                        else -> "标准 (推荐 0.65)"
                    },
                    color = greenAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Slider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    VoiceWakeManager.setSensitivity(context, it)
                },
                valueRange = 0.3f..0.95f,
                steps = 12,
                colors = SliderDefaults.colors(thumbColor = greenAccent, activeTrackColor = greenAccent)
            )

            Text(
                text = "提示：车速较快（胎噪风噪较大）时，建议设为 0.55~0.65 避免误触；停车或静音时可调高灵敏度。",
                fontSize = 11.sp,
                color = Color(0xFF78909C)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. 原车小迪联动与熊猫车机模式接管卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E28), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text("💡 比亚迪原车「小迪」双模与熊猫车机接管", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "借鉴熊猫车机核心架构：深度捕获比亚迪系统广播，无需按键，随时呼叫小迪点歌即播。",
                fontSize = 12.sp,
                color = Color(0xFFB0BEC5)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 开关1：深度接管小迪语音播控
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小迪语音指令深度接管（熊猫车机模式）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("响应「你好小迪播放音乐/播放晴天/下一首」系统级广播", fontSize = 11.sp, color = Color(0xFF90A4AE))
                }
                Switch(
                    checked = wakeConfig.bydXiaoDiHijackDefault,
                    onCheckedChange = { VoiceWakeManager.setBydXiaoDiHijackDefault(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 开关2：小迪唤醒时自动打开千千静听前台大屏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小迪点歌时自动打开千千静听大屏", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("小迪识别成功后自动将千千静听调至前台，展示经典皮肤与动态频谱", fontSize = 11.sp, color = Color(0xFF90A4AE))
                }
                Switch(
                    checked = wakeConfig.bydXiaoDiAutoForeground,
                    onCheckedChange = { VoiceWakeManager.setBydXiaoDiAutoForeground(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 开关3：车辆开机启动自动播放熄火前记忆歌曲
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("车辆开机自动续播记忆歌曲", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("车机通电（ACC ON）后自动后台拉起，精确恢复熄火前曲目与毫秒级进度", fontSize = 11.sp, color = Color(0xFF90A4AE))
                }
                Switch(
                    checked = wakeConfig.autoResumeOnBoot,
                    onCheckedChange = { VoiceWakeManager.setAutoResumeOnBoot(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 按钮：开启无障碍服务（针对部分 DiLink 硬编码酷我/酷狗的系统）
            OutlinedButton(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1976D2))
            ) {
                Text("🛠 启用无障碍接管服务（解决原车强开酷我/酷狗）", fontSize = 12.sp)
            }
        }
    }
}

/** 录制 1.6 秒 16kHz PCM 单声道音频并返回采样数组 */
@SuppressLint("MissingPermission")
private suspend fun recordAudioSample(): ShortArray {
    val sampleRate = VoiceWakeManager.SAMPLE_RATE
    val totalSamples = (sampleRate * (VoiceWakeManager.RECORD_DURATION_MS / 1000.0)).toInt()
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    val recorder = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBuf * 2
    )

    val out = ShortArray(totalSamples)
    try {
        recorder.startRecording()
        var readTotal = 0
        val chunk = ShortArray(800)
        while (readTotal < totalSamples) {
            val count = recorder.read(chunk, 0, (totalSamples - readTotal).coerceAtMost(chunk.size))
            if (count > 0) {
                System.arraycopy(chunk, 0, out, readTotal, count)
                readTotal += count
            } else {
                delay(10)
            }
        }
    } catch (_: Exception) {
    } finally {
        try {
            recorder.stop()
            recorder.release()
        } catch (_: Exception) {}
    }
    return out
}
