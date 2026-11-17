package com.byd.qianqian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.qianqian.model.AudioPreset

/**
 * 车载音响品牌与 3D 环绕声场调谐面板
 * 专为比亚迪全系车型适配：
 * - 原厂标准 4/6/8 扬声器 (声场重塑 + 140Hz 门板防共振)
 * - Dirac 数字调音系统 (微拓宽 + 瞬态平直)
 * - Dynaudio 丹拿 Hi-Fi 原音直通 (消除梳状滤波冲突)
 * - Dynaudio 丹拿 3D 环绕音乐厅 (开阔沉浸)
 * - Devialet 腾势帝瓦雷 SAM (极低频 80Hz 爆发)
 * - 纯净监听直通 (仰望 U8/U7 证据音响 / 无损母带直出)
 */
@Composable
fun AudioProfileSheet(
    currentPreset: AudioPreset,
    onSelectPreset: (AudioPreset) -> Unit,
    onClose: () -> Unit
) {
    val accentGreen = Color(0xFF00E676)
    val cardBg = Color(0xFF141A24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
            .padding(16.dp)
    ) {
        // 顶部标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "车载音响硬件适配 & 3D 环绕调谐",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "根据你的比亚迪车型音响硬件精准匹配，杜绝软硬件 DSP 延时对冲与梳状滤波",
                    color = Color(0xFF90A4AE),
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .background(Color(0xFF1F2937), RoundedCornerShape(6.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("完成", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(AudioPreset.entries) { preset ->
                val isSelected = preset.id == currentPreset.id

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) Color(0xFF12281D) else cardBg,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) accentGreen else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectPreset(preset) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.title,
                            color = if (isSelected) accentGreen else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Text(
                                text = "● 当前生效",
                                color = accentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preset.subtitle,
                        color = if (isSelected) Color(0xFF81C784) else Color(0xFFB0BEC5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = preset.vehicleScope,
                        color = Color(0xFFFFB74D),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preset.dspFeatures,
                        color = Color(0xFF78909C),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
