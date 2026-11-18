package com.byd.qianqian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.qianqian.manager.PlaylistManager

/**
 * 比亚迪车载 USB U盘热插拔监听接收器：
 * 1. 当车主在中央扶手箱或副仪表台插入 FAT32/exFAT 无损音乐 U 盘 (MEDIA_MOUNTED) 时，自动实时扫描新增曲目与同名 .lrc 歌词
 * 2. 当 U 盘拔出 (MEDIA_UNMOUNTED / MEDIA_EJECT) 时，提示并安全刷新本地曲库列表
 */
class UsbStorageReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UsbStorageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val mountPath = intent.data?.path ?: ""
        Log.i(TAG, "收到存储设备事件: action=$action, path=$mountPath")

        when (action) {
            Intent.ACTION_MEDIA_MOUNTED -> {
                PlaylistManager.notifyUsbStatus("检测到U盘接入，正在扫描无损音乐...")
                PlaylistManager.scanLocalAndUsbStorage(context.applicationContext)
            }
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_REMOVED -> {
                PlaylistManager.notifyUsbStatus("U盘已安全弹出，已刷新曲库")
                PlaylistManager.scanLocalAndUsbStorage(context.applicationContext)
            }
        }
    }
}
