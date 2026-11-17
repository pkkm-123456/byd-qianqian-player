package com.byd.qianqian.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.byd.qianqian.bridge.NativeAudioBridge
import kotlinx.coroutines.*

/**
 * 车机全局桌面悬浮歌词：当切换到全屏高德地图导航时，依然可以在屏幕顶部或底部查看千千静听同步双色歌词
 */
class FloatingLyricsWindow(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var textView: TextView? = null
    private var updateJob: Job? = null
    private var isShowing = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 48
    }

    fun toggle() {
        if (isShowing) hide() else show()
    }

    fun show() {
        if (isShowing) return
        val tv = TextView(context).apply {
            text = "千千静听车载桌面歌词"
            textSize = 22f
            setTextColor(Color.parseColor("#00E676"))
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            setPadding(32, 12, 32, 12)
            setBackgroundColor(Color.parseColor("#66070A0E"))

            var downY = 0f
            var startParamY = 0
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        startParamY = layoutParams.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.y = (startParamY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                        windowManager.updateViewLayout(this, layoutParams)
                        true
                    }
                    else -> false
                }
            }
        }
        windowManager.addView(tv, layoutParams)
        textView = tv
        isShowing = true

        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val pos = NativeAudioBridge.getCurrentPositionMs()
                tv.text = NativeAudioBridge.getCurrentLyricLine(pos)
                delay(100L)
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        updateJob?.cancel()
        textView?.let { windowManager.removeView(it) }
        textView = null
        isShowing = false
    }
}
