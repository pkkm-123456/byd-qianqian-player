package com.byd.qianqian.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.qianqian.bridge.NativeAudioBridge
import com.byd.qianqian.manager.PlaylistManager
import kotlinx.coroutines.*

/**
 * 车机全局桌面悬浮歌词 + 经典千千静听「迷你悬浮控条」：
 * 1. 当切换到全屏高德地图导航时，可全屏自由拖拽悬浮于任何位置（支持 X/Y 双轴拖拽）
 * 2. 上行显示当前歌曲名与双色荧光绿同步歌词，右侧集成盲操迷你按钮（上一首 / 播放暂停 / 下一首 / 关闭）
 */
class FloatingLyricsWindow(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootLayout: LinearLayout? = null
    private var titleView: TextView? = null
    private var lyricView: TextView? = null
    private var playPauseBtn: TextView? = null
    private var updateJob: Job? = null
    private var isShowing = false

    private val wmParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = 48
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(Color.parseColor("#D912202E"))
            setStroke(2, Color.parseColor("#664FC3F7"))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = bgDrawable
            setPadding(28, 14, 24, 14)
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 24
            }
        }

        val tvTitle = TextView(context).apply {
            text = PlaylistManager.currentTrack.value?.let { "${it.title} - ${it.artist}" } ?: "千千静听车载版"
            textSize = 12f
            setTextColor(Color.parseColor("#90CAF9"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = 420
        }

        val tvLyric = TextView(context).apply {
            text = "千千静听 · 车载 Hi-Fi 歌词"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = 420
        }

        textColumn.addView(tvTitle)
        textColumn.addView(tvLyric)
        container.addView(textColumn)

        fun createMiniButton(label: String, textColorHex: String = "#E0F7FA", onClick: () -> Unit): TextView {
            return TextView(context).apply {
                text = label
                textSize = 17f
                setTextColor(Color.parseColor(textColorHex))
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
                setOnClickListener { onClick() }
            }
        }

        val btnPrev = createMiniButton("⏮") { PlaylistManager.skipPrevious() }
        val btnPlayPause = createMiniButton(if (PlaylistManager.isPlaying.value) "⏸" else "▶", "#00E676") {
            PlaylistManager.togglePlayPause()
        }
        val btnNext = createMiniButton("⏭") { PlaylistManager.skipNext() }
        val btnClose = createMiniButton("✕", "#90A4AE") { hide() }

        container.addView(btnPrev)
        container.addView(btnPlayPause)
        container.addView(btnNext)
        container.addView(btnClose)

        var downX = 0f
        var downY = 0f
        var startParamX = 0
        var startParamY = 0
        textColumn.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startParamX = wmParams.x
                    startParamY = wmParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    wmParams.x = startParamX + (event.rawX - downX).toInt()
                    wmParams.y = (startParamY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    windowManager.updateViewLayout(container, wmParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, wmParams)
        rootLayout = container
        titleView = tvTitle
        lyricView = tvLyric
        playPauseBtn = btnPlayPause
        isShowing = true

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val track = PlaylistManager.currentTrack.value
                tvTitle.text = if (track != null) "${track.title} · ${track.artist}" else "千千静听 · 车载版"
                val pos = NativeAudioBridge.getCurrentPositionMs()
                val lyric = NativeAudioBridge.getCurrentLyricLine(pos)
                if (lyric.isNotEmpty()) {
                    tvLyric.text = lyric
                }
                btnPlayPause.text = if (PlaylistManager.isPlaying.value) "⏸" else "▶"
                delay(120L)
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        updateJob?.cancel()
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        rootLayout = null
        titleView = null
        lyricView = null
        playPauseBtn = null
        isShowing = false
    }
}
