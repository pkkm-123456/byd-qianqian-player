package com.byd.qianqian.ui

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
import com.byd.qianqian.manager.NasSettingsManager
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.manager.VoiceWakeManager

/**
 * 统一「设置中心」聚合入口：
 * 点击主界面右上角单个「⚙」图标进入，保持播放器主视觉纯净经典，不堆叠按钮。
 */
@Composable
fun SettingsHubSheet(
    onOpenAudioProfile: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenNasSettings: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val currentPreset by PlaylistManager.currentAudioPreset.collectAsState()
    val wakeConfig by VoiceWakeManager.config.collectAsState()
    val nasConfig = remember { NasSettingsManager.load(context) }

    val cardBg = Color(0xFF121B26)
    val accentBlue = Color(0xFF64B5F6)
    val accentGreen = Color(0xFF00E676)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E14))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 顶部返回栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<  千千静听 · 车机设置中心",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose() }
            )
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A2634), RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("完成", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. 车载音响与 DSP 声场设置入口
        SettingsEntryCard(
            icon = "🎛️",
            title = "车载音响与丹拿/帝瓦雷 DSP 调校",
            subtitle = "当前预设：${currentPreset.title}",
            accentColor = accentBlue,
            bgColor = cardBg,
            onClick = onOpenAudioProfile
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 小迪语音、熊猫接管与声纹唤醒设置入口
        SettingsEntryCard(
            icon = "🎤",
            title = "小迪语音接管、熊猫车机模式与唤醒词",
            subtitle = "唤醒词「${wakeConfig.wakeWordText}」 · 小迪接管：${if (wakeConfig.bydXiaoDiHijackDefault) "已开启" else "已关闭"}",
            accentColor = Color(0xFFFFD54F),
            bgColor = cardBg,
            onClick = onOpenVoiceSettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 飞牛私有云 NAS WebDAV 挂载入口
        SettingsEntryCard(
            icon = "🗄️",
            title = "飞牛私有云 NAS (fnOS) 与无损缓存",
            subtitle = if (nasConfig.serverUrl.isNotEmpty()) "已配置：${nasConfig.serverUrl}" else "未配置 WebDAV 远程曲库",
            accentColor = Color(0xFF81D4FA),
            bgColor = cardBg,
            onClick = onOpenNasSettings
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 4. 常用车规快捷开关（无需进二级菜单即可一键控制）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x224FC3F7), RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            Text("🚗 常用车规快捷开关", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("车辆开机自动续播记忆歌曲", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("通电（ACC ON）后自动拉起并恢复熄火前毫秒级进度", fontSize = 11.sp, color = Color(0xFF90A4AE))
                }
                Switch(
                    checked = wakeConfig.autoResumeOnBoot,
                    onCheckedChange = { VoiceWakeManager.setAutoResumeOnBoot(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentGreen)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小迪点歌时自动打开千千静听大屏", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("通过「你好小迪」点歌后自动将播放器调至前台", fontSize = 11.sp, color = Color(0xFF90A4AE))
                }
                Switch(
                    checked = wakeConfig.bydXiaoDiAutoForeground,
                    onCheckedChange = { VoiceWakeManager.setBydXiaoDiAutoForeground(context, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentGreen)
                )
            }
        }
    }
}

@Composable
private fun SettingsEntryCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x224FC3F7), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(text = icon, fontSize = 22.sp)
            Column {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = accentColor, fontSize = 12.sp)
            }
        }
        Text(text = "›", color = Color(0xFF78909C), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
