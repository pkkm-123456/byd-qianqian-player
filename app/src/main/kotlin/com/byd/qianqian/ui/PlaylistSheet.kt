package com.byd.qianqian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.model.Track

@Composable
fun PlaylistSheet(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onTrackSelect: (Track) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val accentGreen = Color(0xFF00E676)
    val cardBg = Color(0xFF141A24)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
            .padding(16.dp)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "播放列表 (${tracks.size} 首)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1F2937), RoundedCornerShape(6.dp))
                        .clickable { PlaylistManager.scanLocalAndUsbStorage(context) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("扫描U盘/存储", color = accentGreen, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1F2937), RoundedCornerShape(6.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("关闭", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无曲目，请插入含音乐的 FAT32/exFAT U盘或配置飞牛 NAS", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tracks) { idx, item ->
                    val isCurrent = item.id == currentTrack?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) Color(0xFF162520) else cardBg,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onTrackSelect(item) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = (idx + 1).toString().padStart(2, '0'),
                                color = if (isCurrent) accentGreen else Color(0xFF757575),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Column {
                                Text(
                                    text = item.title,
                                    color = if (isCurrent) accentGreen else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.artist} · ${if (item.isNas) "NAS" else "本地"}",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        if (isCurrent && isPlaying) {
                            Text("▶ 播放中", color = accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
