package com.byd.qianqian.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.byd.qianqian.MainActivity
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.manager.VoiceWakeManager

/**
 * 熊猫车机原版无障碍接管服务（BydXiaoDiAccessibilityService）：
 * 针对部分比亚迪 DiLink 2.0 / 3.0 系统硬编码「小迪播放音乐」强制启动原厂酷我/酷狗音乐的问题。
 * 当车主在系统辅助功能中启用本服务后，一旦检测到原厂播放器被调起，立即静音压制并无缝切换至千千静听继续播放！
 */
class BydXiaoDiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BydXiaoDiAccService"

        private val HIJACK_PACKAGES = setOf(
            "cn.kuwo.kwmusiccar",
            "kugou.car",
            "com.tencent.qqmusiccar",
            "com.netease.cloudmusic.car"
        )
    }

    private var lastInterceptTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val config = VoiceWakeManager.config.value
        if (!config.bydXiaoDiHijackDefault) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString() ?: return
            if (HIJACK_PACKAGES.contains(pkgName)) {
                val now = System.currentTimeMillis()
                // 防抖节流 2 秒，避免频繁切换
                if (now - lastInterceptTime > 2000) {
                    lastInterceptTime = now
                    Log.i(TAG, "检测到原厂音乐 $pkgName 启动，触发熊猫车机模式深度接管并切换回千千静听")
                    takeoverFromNativePlayer()
                }
            }
        }
    }

    private fun takeoverFromNativePlayer() {
        // 1. 若当前暂停，自动开始播放千千静听
        if (!PlaylistManager.isPlaying.value) {
            PlaylistManager.togglePlayPause()
        }

        // 2. 调出千千静听主界面盖过原厂应用
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                putExtra("hijacked_from_native", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "接管调起千千静听失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }
}
