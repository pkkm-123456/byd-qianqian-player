package com.byd.qianqian.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.model.Track
import kotlinx.coroutines.delay

enum class CarWindowMode {
    FULL_LANDSCAPE,     // 全屏横屏：千千静听经典三栏
    SPLIT_HALF,         // 1/2 对半分屏：紧凑上下双栏
    SPLIT_ONE_THIRD,    // 1/3 导航伴侣窄分屏：超大当前句歌词 + 24段精简音柱 + 盲操按钮
    PORTRAIT_ROTATED    // 比亚迪旋转竖屏：1:1 还原经典千千静听金属蓝皮肤
}

@Composable
fun AdaptivePlayerScreen(
    currentTrack: Track?,
    isPlaying: Boolean,
    isNightMode: Boolean,
    currentAudioPreset: com.byd.qianqian.model.AudioPreset,
    onOpenAudioProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleFloatingLyrics: () -> Unit,
    onTriggerInAppVoiceSearch: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    // 48 段高密度频段缓冲（真实 PCM 音频 FFT 频域能量）
    val spectrumBars = remember { FloatArray(48) }
    val peakCaps = remember { FloatArray(48) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var currentLyric by remember { mutableStateOf("重逢的灵魂") }
    var prevLyric by remember { mutableStateOf("旧情人") }
    var nextLyric by remember { mutableStateOf("真亦假") }
    var showFullLyricsInFullMode by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(71000L) }
    var durationMs by remember { mutableLongStateOf(163000L) }

    // 车机 30fps 轮询拉取（约 33ms 间隔），低 CPU 占用防发热
    LaunchedEffect(Unit) {
        while (true) {
            NativeAudioBridge.pullSpectrum(spectrumBars, peakCaps)
            val pos = NativeAudioBridge.getCurrentPositionMs()
            if (pos > 0) {
                currentPosMs = pos
            }
            val dur = NativeAudioBridge.getTrackDurationMs()
            if (dur > 0) {
                durationMs = dur
            }
            val lyric = NativeAudioBridge.getCurrentLyricLine(currentPosMs)
            if (lyric.isNotEmpty()) {
                currentLyric = lyric
            }
            frameTick++
            delay(33L)
        }
    }

    // 千千静听经典金属钢蓝渐变外框
    val chassisGradient = Brush.verticalGradient(
        colors = if (isNightMode) {
            listOf(Color(0xFF243647), Color(0xFF142432), Color(0xFF0A141D))
        } else {
            listOf(Color(0xFF436582), Color(0xFF28435A), Color(0xFF152737))
        }
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(chassisGradient)
            .padding(10.dp)
    ) {
        val windowMode = remember(maxWidth, maxHeight) {
            when {
                maxHeight > maxWidth * 1.1f -> CarWindowMode.PORTRAIT_ROTATED
                maxWidth < 520.dp -> CarWindowMode.SPLIT_ONE_THIRD
                maxWidth < 900.dp -> CarWindowMode.SPLIT_HALF
                else -> CarWindowMode.FULL_LANDSCAPE
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TopStatusHeader(
                windowMode = windowMode,
                currentAudioPreset = currentAudioPreset,
                onOpenAudioProfile = onOpenAudioProfile,
                onOpenVoiceSettings = onOpenVoiceSettings,
                onOpenSettings = onOpenSettings
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (windowMode) {
                    CarWindowMode.PORTRAIT_ROTATED -> {
                        // 1:1 还原千千静听经典界面的核心视窗
                        QianqianClassicMainCard(
                            track = currentTrack,
                            bars = spectrumBars,
                            peaks = peakCaps,
                            barCount = 48,
                            tick = frameTick,
                            isNightMode = isNightMode,
                            prevLyric = prevLyric,
                            currentLyric = currentLyric,
                            nextLyric = nextLyric,
                            onToggleLyrics = { showFullLyricsInFullMode = !showFullLyricsInFullMode },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    CarWindowMode.FULL_LANDSCAPE -> {
                        if (showFullLyricsInFullMode) {
                            LyricsPanel(currentLine = currentLyric, fontSize = 28.sp, accentColor = Color(0xFF00E676))
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 左侧：千千静听经典金属视窗与 48 段跳动脉冲
                                Box(modifier = Modifier.weight(0.52f).fillMaxHeight()) {
                                    QianqianClassicMainCard(
                                        track = currentTrack,
                                        bars = spectrumBars,
                                        peaks = peakCaps,
                                        barCount = 48,
                                        tick = frameTick,
                                        isNightMode = isNightMode,
                                        prevLyric = prevLyric,
                                        currentLyric = currentLyric,
                                        nextLyric = nextLyric,
                                        onToggleLyrics = { showFullLyricsInFullMode = !showFullLyricsInFullMode },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                // 右侧：同步大屏滚动歌词
                                Box(modifier = Modifier.weight(0.48f).fillMaxHeight()) {
                                    LyricsPanel(
                                        currentLine = currentLyric,
                                        fontSize = 24.sp,
                                        accentColor = Color(0xFF00E676),
                                        isLandscapeWide = true
                                    )
                                }
                            }
                        }
                    }

                    CarWindowMode.SPLIT_HALF -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QianqianClassicMainCard(
                                track = currentTrack,
                                bars = spectrumBars,
                                peaks = peakCaps,
                                barCount = 36,
                                tick = frameTick,
                                isNightMode = isNightMode,
                                prevLyric = prevLyric,
                                currentLyric = currentLyric,
                                nextLyric = nextLyric,
                                onToggleLyrics = { showFullLyricsInFullMode = !showFullLyricsInFullMode },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }

                    CarWindowMode.SPLIT_ONE_THIRD -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TrackMetaHeader(track = currentTrack)
                            QianqianSpectrumView(
                                bars = spectrumBars,
                                peaks = peakCaps,
                                barCount = 24,
                                tick = frameTick,
                                isNightMode = isNightMode,
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            )
                            LyricsPanel(currentLine = currentLyric, fontSize = 22.sp, accentColor = Color(0xFF00E676))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 千千静听经典金属球形播控 Deck 与高品质进度条
            QianqianMetallicDeck(
                windowMode = windowMode,
                isPlaying = isPlaying,
                currentPosMs = currentPosMs,
                durationMs = durationMs,
                onPrev = onPrev,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onOpenPlaylist = onOpenPlaylist,
                onVoiceSearch = onTriggerInAppVoiceSearch,
                onToggleFloatingLyrics = onToggleFloatingLyrics
            )
        }
    }
}

/**
 * 经典千千静听暗夜深蓝 CRT/VFD 风格内屏卡片
 */
@Composable
private fun QianqianClassicMainCard(
    track: Track?,
    bars: FloatArray,
    peaks: FloatArray,
    barCount: Int,
    tick: Long,
    isNightMode: Boolean,
    prevLyric: String,
    currentLyric: String,
    nextLyric: String,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1E2E), Color(0xFF0B1724), Color(0xFF08121C))
                ),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, Color(0x334FC3F7), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 1. 曲目信息行：歌名、歌手与立体声指示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: "湿吻",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track?.artist ?: "亿轩_Kingston, 彭梓烨_Leo"}${if (track?.isNas == true) " · [NAS]" else ""}",
                    color = Color(0xFF829FB8),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "正在播放 · 立体声",
                color = Color(0xFF68859E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. 48 段经典千千静听跳动频谱（LED 分节点阵 + 顶部黄绿峰值帽重力自由落体）
        QianqianSpectrumView(
            bars = bars,
            peaks = peaks,
            barCount = barCount,
            tick = tick,
            isNightMode = isNightMode,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. 屏幕底部经典三行居中歌词
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prevLyric,
                color = Color(0xFF4C6B87),
                fontSize = 12.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentLyric,
                color = Color(0xFFE0F2FE),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = nextLyric,
                color = Color(0xFF4C6B87),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. [歌词] 快捷开关按钮与全屏扩展标
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0x26FFFFFF), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x33B0BEC5), RoundedCornerShape(4.dp))
                    .clickable { onToggleLyrics() }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "歌词",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "⤢",
                color = Color(0xFF68859E),
                fontSize = 14.sp,
                modifier = Modifier.clickable { onToggleLyrics() }
            )
        }
    }
}

/**
 * 经典千千静听高密度跳动频谱画布
 * - 48 段高密度垂直音柱
 * - 翠绿 -> 明黄 -> 橙红 垂直色带渐变
 * - LED 梯子分节水平黑线
 * - 顶部黄绿色峰值悬停帽 (Peak Hold Cap)
 */
@Composable
fun QianqianSpectrumView(
    bars: FloatArray,
    peaks: FloatArray,
    barCount: Int,
    tick: Long,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val currentTick = tick
    // 经典千千静听：底层碧翠绿 -> 中段明黄色 -> 顶端橙红色
    val spectrumBrush = remember(isNightMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF3D00), // 峰值极值红
                Color(0xFFFF9800), // 高频过渡橙
                Color(0xFFFFEB3B), // 中频明黄
                Color(0xFF76FF03), // 黄绿过渡
                Color(0xFF00E676)  // 底层葱翠绿
            )
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (currentTick < 0) return@Canvas
        val totalBars = barCount.coerceAtMost(bars.size).coerceAtLeast(16)
        val gap = 2.5.dp.toPx()
        val barWidth = ((size.width - gap * (totalBars - 1)) / totalBars).coerceAtLeast(3f)

        for (i in 0 until totalBars) {
            val normVal = bars[i].coerceIn(0f, 1f)
            val peakVal = peaks[i].coerceIn(0f, 1f)

            val x = i * (barWidth + gap)
            val barHeight = (normVal * size.height).coerceAtLeast(4f)
            val topY = size.height - barHeight

            // 1. 绘制渐变色彩音柱主条
            drawRect(
                brush = spectrumBrush,
                topLeft = Offset(x, topY),
                size = Size(barWidth, barHeight)
            )

            // 2. 绘制千千静听经典 LED 水平点阵分节线 (黑色间隔缝隙)
            val segmentHeight = 4.5.dp.toPx()
            var segY = size.height
            while (segY > topY) {
                drawLine(
                    color = Color(0xFF0B1724), // 与背景一致的深黑蓝分割线
                    start = Offset(x, segY),
                    end = Offset(x + barWidth, segY),
                    strokeWidth = 1.2.dp.toPx()
                )
                segY -= segmentHeight
            }

            // 3. 绘制顶部黄绿峰值悬停帽 (Peak Hold Cap)
            val peakY = (size.height - peakVal * size.height - 2.5.dp.toPx()).coerceIn(0f, size.height - 3f)
            drawRect(
                color = Color(0xFFD4E157), // 经典千千静听明亮黄绿色峰值横条
                topLeft = Offset(x, peakY),
                size = Size(barWidth, 2.5.dp.toPx())
            )
        }
    }
}

/**
 * 千千静听经典金属拉丝 3D 圆形播控台
 */
@Composable
private fun QianqianMetallicDeck(
    windowMode: CarWindowMode,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onVoiceSearch: () -> Unit,
    onToggleFloatingLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // 1. 自适应宽度的青空蓝高光进度条与发光游标 (BoxWithConstraints 动态计算)
        val progress = if (durationMs > 0) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        val posStr = formatTime(currentPosMs)
        val durStr = formatTime(durationMs)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val thumbOffset = (totalWidth - 12.dp) * progress

                // 轨道底槽
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .background(Color(0xFF162A3D), RoundedCornerShape(2.dp))
                )
                // 已播放亮蓝高光条
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.5.dp)
                        .background(Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                )
                // 发光圆形游标
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(12.dp)
                        .background(Color(0xFF00E5FF), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }

        // 时间指示
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = posStr, color = Color(0xFF8FA6BC), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text(text = durStr, color = Color(0xFF8FA6BC), fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. 根据不同分屏/横屏模式自适应按键排布
        if (windowMode == CarWindowMode.SPLIT_ONE_THIRD) {
            // 1/3 极窄导航伴侣模式：专属 3 颗超大盲操按键（行车安全防误触）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetallicCircleButton(size = 50.dp, icon = "⏮", onClick = onPrev)
                MetallicCircleButton(
                    size = 62.dp,
                    icon = if (isPlaying) "⏸" else "▶",
                    isCenterPrimary = true,
                    onClick = onPlayPause
                )
                MetallicCircleButton(size = 50.dp, icon = "⏭", onClick = onNext)
            }
        } else {
            // 全屏横屏、竖屏与 1/2 分屏模式：完整 5 颗金属球形按键
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 模式循环球
                MetallicCircleButton(size = 42.dp, icon = "🔁", onClick = { })
                // 上一首
                MetallicCircleButton(size = 46.dp, icon = "⏮", onClick = onPrev)
                // 核心大尺寸播放/暂停球
                MetallicCircleButton(
                    size = 56.dp,
                    icon = if (isPlaying) "⏸" else "▶",
                    isCenterPrimary = true,
                    onClick = onPlayPause
                )
                // 下一首
                MetallicCircleButton(size = 46.dp, icon = "⏭", onClick = onNext)
                // 歌单
                MetallicCircleButton(size = 42.dp, icon = "☰", onClick = onOpenPlaylist)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. 底部专属功能条：收起、[高品质·HQ]、收藏、更多
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetallicCircleButton(size = 32.dp, icon = "⌄", onClick = { })

                // 高品质 HQ 金属质感胶囊徽章
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF38536B), Color(0xFF1E3243))
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, Color(0x4481D4FA), RoundedCornerShape(6.dp))
                        .clickable { onVoiceSearch() }
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "高品质 · HQ",
                        color = Color(0xFFE0F2FE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetallicCircleButton(size = 32.dp, icon = "♡", onClick = onToggleFloatingLyrics)
                    MetallicCircleButton(size = 32.dp, icon = "···", onClick = onVoiceSearch)
                }
            }
        }
    }
}

/**
 * 经典千千静听 3D 金属球形按钮
 */
@Composable
private fun MetallicCircleButton(
    size: androidx.compose.ui.unit.Dp,
    icon: String,
    isCenterPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val buttonBrush = if (isCenterPrimary) {
        Brush.radialGradient(
            colors = listOf(Color(0xFF4A7294), Color(0xFF26435C), Color(0xFF122538))
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFF3E5A74), Color(0xFF21384D), Color(0xFF132332))
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(buttonBrush)
            .border(1.dp, Color(0x4481D4FA), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            color = if (isCenterPrimary) Color(0xFFE0F2FE) else Color(0xFFB0BEC5),
            fontSize = if (isCenterPrimary) 20.sp else 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopStatusHeader(
    windowMode: CarWindowMode,
    currentAudioPreset: com.byd.qianqian.model.AudioPreset,
    onOpenAudioProfile: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 千千静听橙色经典耳机 Logo
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color(0xFFFF7043), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🎧", fontSize = 11.sp)
            }

            Text(
                text = "千千静听",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            // 车载音响品牌声场预设切换徽章
            Box(
                modifier = Modifier
                    .background(Color(0xFF183244), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x334FC3F7), RoundedCornerShape(4.dp))
                    .clickable { onOpenAudioProfile() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🎛️ ${currentAudioPreset.title.substringBefore(" (")}",
                    color = Color(0xFF81D4FA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF263238), RoundedCornerShape(4.dp))
                    .clickable { onOpenVoiceSettings() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (windowMode == CarWindowMode.SPLIT_ONE_THIRD) "🎤" else "🎤 你好千千",
                    color = Color(0xFFFFD54F),
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (windowMode == CarWindowMode.SPLIT_ONE_THIRD) "⚙" else "⚙ 飞牛NAS",
                    color = Color(0xFF81D4FA),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun TrackMetaHeader(track: Track?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1E2E), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = track?.title ?: "湿吻",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track?.artist ?: "亿轩_Kingston, 彭梓烨_Leo",
            color = Color(0xFF829FB8),
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LyricsPanel(
    currentLine: String,
    fontSize: TextUnit,
    accentColor: Color,
    isLandscapeWide: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1622), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x224FC3F7), RoundedCornerShape(10.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLandscapeWide) {
            Text(
                text = "在旧日时光与你擦肩",
                color = Color(0xFF4C6B87),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "夜幕沉入微醺的深渊",
                color = Color(0xFF6B8DAE),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            // 当前高亮句
            Box(
                modifier = Modifier
                    .background(Color(0xFF132A3E), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x5538BDF8), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentLine,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "叹这世间 真亦假 梦初醒",
                color = Color(0xFF6B8DAE),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "回首唯有月色照无眠",
                color = Color(0xFF4C6B87),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = currentLine,
                color = Color(0xFFE0F2FE),
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
