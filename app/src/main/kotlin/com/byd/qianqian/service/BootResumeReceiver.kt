package com.byd.qianqian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 车辆点火通电 (BOOT_COMPLETED / QUICKBOOT_POWERON) 后自动拉起播放服务，恢复熄火前毫秒级进度
 */
class BootResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val serviceIntent = Intent(context, CarPlaybackService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
