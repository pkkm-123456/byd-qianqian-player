package com.byd.qianqian.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.manager.NasConfig
import com.byd.qianqian.manager.NasSettingsManager
import com.byd.qianqian.manager.PlaylistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 100% 对齐车载截图的「系统设置 · 飞牛 NAS 直存 (WebDAV)」界面：
 * 1. 本地持久化保存服务器地址、账号、密码、目标目录与缓存模式
 * 2. 异步 IO 执行 HTTP PROPFIND 连通性测试与 5005 端口智能校验，杜绝 MainThread 阻塞
 * 3. 启用时自动调用 WebDAV 目录扫描并将发现的全部 NAS 曲目秒级注入播放列表
 * 4. 底部集成全功能迷你播控条（对齐截图真机样式）
 */
@Composable
fun FeiniuNasSettingsSheet(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedConfig = remember { NasSettingsManager.load(context) }

    var webdavEnabled by remember { mutableStateOf(savedConfig.webdavEnabled) }
    var cacheModeEnabled by remember { mutableStateOf(savedConfig.cacheModeEnabled) }
    var autoArchiveOnFavorite by remember { mutableStateOf(savedConfig.autoArchiveOnFavorite) }

    var serverUrl by remember { mutableStateOf(savedConfig.serverUrl) }
    var username by remember { mutableStateOf(savedConfig.username) }
    var password by remember { mutableStateOf(savedConfig.password) }
    var targetDir by remember { mutableStateOf(savedConfig.targetDir) }
    var cacheQuotaGb by remember { mutableFloatStateOf(savedConfig.cacheQuotaGb) }

    var isConnecting by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf("") }
    val greenAccent = Color(0xFF19A974)
    val cardBg = Color(0xFFF7F9FA)

    val currentTrack by PlaylistManager.currentTrack.collectAsState()
    val isPlaying by PlaylistManager.isPlaying.collectAsState()

    val hasWrongFeiniuPort = remember(serverUrl) {
        serverUrl.contains(":5666") || serverUrl.contains(":5667")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 主设置滚动区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "<  系统设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.clickable { onClose() }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. 下载设置卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("下载设置", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
                    Text("无损 · 封面&歌词", fontSize = 12.sp, color = Color(0xFF777777))
                }
                Text(">", fontSize = 18.sp, color = Color(0xFFB0BEC5))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. NAS 直存 (WebDAV) 卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("NAS 直存 (WebDAV)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
                    Text(
                        text = if (webdavEnabled) "已开启 · 下载自动归档到飞牛" else "未开启",
                        fontSize = 12.sp,
                        color = if (webdavEnabled) greenAccent else Color(0xFF888888)
                    )
                }
                Switch(
                    checked = webdavEnabled,
                    onCheckedChange = { webdavEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NAS 歌曲播放方式
            Text("NAS 歌曲播放方式", fontSize = 14.sp, color = Color(0xFF555555))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 在线播放
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (!cacheModeEnabled) greenAccent else Color(0xFFEEF3F7),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { cacheModeEnabled = false }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "在线播放",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!cacheModeEnabled) Color.White else Color(0xFF2C3E50)
                    )
                    Text(
                        text = "不占本地空间",
                        fontSize = 12.sp,
                        color = if (!cacheModeEnabled) Color(0xFFE0F2F1) else Color(0xFF7F8C8D)
                    )
                }

                // 下载缓存
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (cacheModeEnabled) greenAccent else Color(0xFFEEF3F7),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { cacheModeEnabled = true }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "下载缓存",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (cacheModeEnabled) Color.White else Color(0xFF2C3E50)
                    )
                    Text(
                        text = "可离线听",
                        fontSize = 12.sp,
                        color = if (cacheModeEnabled) Color(0xFFE0F2F1) else Color(0xFF7F8C8D)
                    )
                }
            }

            // 缓存配额滑块
            if (cacheModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("车机本地缓存上限（超出按时间淘汰旧歌）", fontSize = 13.sp, color = Color(0xFF333333))
                    Text("${cacheQuotaGb.toInt()} GB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = greenAccent)
                }
                Slider(
                    value = cacheQuotaGb,
                    onValueChange = { cacheQuotaGb = it },
                    valueRange = 1f..32f,
                    steps = 30,
                    colors = SliderDefaults.colors(thumbColor = greenAccent, activeTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 收藏时自动下载并归档
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("收藏时自动下载并归档", fontSize = 15.sp, color = Color(0xFF222222))
                Switch(
                    checked = autoArchiveOnFavorite,
                    onCheckedChange = { autoArchiveOnFavorite = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = greenAccent)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 服务器地址
            Text("服务器地址", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it.trim() },
                placeholder = { Text("http://192.xx.xx:5005") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(8.dp))
            )
            Text(
                text = if (hasWrongFeiniuPort) {
                    "⚠️ 检测到 5666/5667 飞牛网页端口！请改为 WebDAV 端口 5005 (HTTP) 或 5006 (HTTPS)，否则连不上"
                } else {
                    "WebDAV 地址，端口填 5005（5666/5667 是飞牛网页界面端口，填了会连不上）"
                },
                fontSize = 12.sp,
                color = if (hasWrongFeiniuPort) Color(0xFFD32F2F) else Color(0xFF777777),
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 用户名
            Text("用户名", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                placeholder = { Text("飞牛的登录账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 密码
            Text("密码", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("飞牛的登录密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 目标目录
            Text("目标目录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = targetDir,
                onValueChange = { targetDir = it.trim() },
                placeholder = { Text("Music") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().background(cardBg, RoundedCornerShape(8.dp))
            )
            Text(
                text = "相对 WebDAV 根目录的路径（根 = 共享文件夹列表），可多级，如 Music 或 Music/华语；留空 = 根目录，文件会直接落在共享文件夹最外层。飞牛里新建的文件夹要加进「共享」，否则 WebDAV 看不到。\n先在飞牛「设置 -> 文件服务 -> WebDAV」里启用服务（默认端口 5005）。",
                fontSize = 12.sp,
                color = Color(0xFF777777),
                modifier = Modifier.padding(top = 4.dp)
            )

            if (testResultMsg.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = testResultMsg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (testResultMsg.startsWith("连接成功")) greenAccent else Color(0xFFD32F2F)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 底部操作双按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 测试连接
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFFEEF3F7), RoundedCornerShape(10.dp))
                        .clickable(enabled = !isConnecting) {
                            isConnecting = true
                            testResultMsg = "正在测试连接飞牛 WebDAV..."
                            coroutineScope.launch(Dispatchers.IO) {
                                val res = NativeAudioBridge.testFeiniuWebdavConnection(
                                    serverUrl,
                                    username,
                                    password,
                                    targetDir
                                )
                                withContext(Dispatchers.Main) {
                                    isConnecting = false
                                    testResultMsg = res
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isConnecting) "连接中..." else "测试连接",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                }

                // 保存并启用
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(greenAccent, RoundedCornerShape(10.dp))
                        .clickable {
                            val config = NasConfig(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                targetDir = targetDir,
                                webdavEnabled = webdavEnabled,
                                cacheModeEnabled = cacheModeEnabled,
                                autoArchiveOnFavorite = autoArchiveOnFavorite,
                                cacheQuotaGb = cacheQuotaGb
                            )
                            NasSettingsManager.save(context, config)

                            val quotaBytes = (cacheQuotaGb.toLong()) * 1024L * 1024L * 1024L
                            NativeAudioBridge.configureFeiniuWebdav(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                targetDir = targetDir,
                                cacheModeEnabled = cacheModeEnabled,
                                autoArchiveOnFav = autoArchiveOnFavorite,
                                quotaBytes = quotaBytes
                            )

                            // 触发后台曲库扫描并将曲目同步到播放列表
                            coroutineScope.launch(Dispatchers.IO) {
                                val tracksJson = NativeAudioBridge.syncNasLibrary(
                                    serverUrl,
                                    username,
                                    password,
                                    targetDir
                                )
                                withContext(Dispatchers.Main) {
                                    PlaylistManager.importNasTracksFromJson(tracksJson)
                                }
                            }
                            onClose()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("保存并启用", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // 底部迷你播控栏（完美对齐真机截图底部播放条）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF2C3E50), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎵", fontSize = 20.sp)
                }
                Column {
                    Text(
                        text = currentTrack?.title ?: "真的爱你",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentTrack?.artist ?: "Beyond"} · 飞牛NAS",
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(greenAccent.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        .clickable { PlaylistManager.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = greenAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("☰", fontSize = 22.sp, color = Color(0xFF555555))
            }
        }
    }
}
