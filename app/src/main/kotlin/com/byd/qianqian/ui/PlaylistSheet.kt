package com.byd.qianqian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

private enum class PlaylistFilter(val label: String) {
    ALL("全部"),
    LOCAL("本地 & U盘"),
    NAS("飞牛 NAS")
}

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

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PlaylistFilter.ALL) }

    val filteredTracks = remember(tracks, searchQuery, activeFilter) {
        tracks.filter { track ->
            val matchesFilter = when (activeFilter) {
                PlaylistFilter.ALL -> true
                PlaylistFilter.LOCAL -> !track.isNas
                PlaylistFilter.NAS -> track.isNas
            }
            val matchesQuery = searchQuery.isBlank() ||
                track.title.contains(searchQuery, ignoreCase = true) ||
                track.artist.contains(searchQuery, ignoreCase = true) ||
                track.album.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

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
                text = "播放列表 (${filteredTracks.size}/${tracks.size} 首)",
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

        Spacer(modifier = Modifier.height(10.dp))

        // 搜索框与来源分类筛选
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("输入歌名或歌手快速定位...", color = Color(0xFF607D8B), fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentGreen,
                    unfocusedBorderColor = Color(0xFF263238)
                ),
                modifier = Modifier.weight(1f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlaylistFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) Color(0xFF16352B) else Color(0xFF141A24),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) accentGreen else Color(0xFF263238),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { activeFilter = filter }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter.label,
                            color = if (selected) accentGreen else Color(0xFF90A4AE),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tracks.isEmpty()) "暂无曲目，请插入含音乐的 FAT32/exFAT U盘或配置飞牛 NAS" else "未找到匹配「$searchQuery」的歌曲",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredTracks) { idx, item ->
                    val isCurrent = item.id == currentTrack?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) Color(0xFF162520) else cardBg,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isCurrent) 1.dp else 0.dp,
                                color = if (isCurrent) Color(0x5500E676) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
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
                                    text = "${item.artist} · ${if (item.isNas) "飞牛 NAS" else "本地/U盘"}",
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
