package com.byd.qianqian.voice

import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.qianqian.MainActivity
import com.byd.qianqian.manager.PlaylistManager
import com.byd.qianqian.manager.VoiceWakeManager

/**
 * 比亚迪小迪车载智能语音广播接收器（借鉴与升级自熊猫车机核心架构）：
 * 1. 深度接管小迪系统级广播 (com.byd.voice.action.MUSIC_CONTROL / VOICE_COMMAND)
 * 2. 支持「你好小迪，播放音乐」、「播放周杰伦的晴天」、「下一首」、「暂停」等无缝调度
 * 3. 智能解析曲名、歌手与口语化指令，联动播放器与 SQLite FTS5 引擎定位曲目
 * 4. 唤醒或点歌时自动将千千静听前台大屏调出，展示复古金属皮肤与动态频谱
 */
class BydXiaoDiReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BydXiaoDiReceiver"

        const val ACTION_BYD_MUSIC_CONTROL = "com.byd.voice.action.MUSIC_CONTROL"
        const val ACTION_BYD_MEDIA_CONTROL = "com.byd.voice.action.MEDIA_CONTROL"
        const val ACTION_BYD_WAKEUP = "com.byd.voice.action.WAKEUP"
        const val ACTION_BYD_VOICE_CMD = "com.byd.dilink.action.VOICE_COMMAND"
        const val ACTION_IFLYTEK_VOICE_CMD = "com.iflytek.cutefly.speechclient.ACTION_VOICE_COMMAND"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "收到比亚迪车机语音广播 action=$action, extras=${intent.extras}")

        val config = VoiceWakeManager.config.value
        if (!config.bydXiaoDiHijackDefault) {
            Log.d(TAG, "用户已关闭小迪语音接管，略过处理")
            return
        }

        // 提取指令与搜索关键词
        val cmd = intent.getStringExtra("action")
            ?: intent.getStringExtra("command")
            ?: intent.getStringExtra("cmd")
            ?: intent.getStringExtra("type")
            ?: ""

        val rawText = intent.getStringExtra("raw_text")
            ?: intent.getStringExtra("asr_text")
            ?: intent.getStringExtra("voice_cmd")
            ?: intent.getStringExtra("text")
            ?: intent.getStringExtra("speech")
            ?: ""

        val query = intent.getStringExtra(SearchManager.QUERY)
            ?: intent.getStringExtra("keyword")
            ?: intent.getStringExtra("query")
            ?: intent.getStringExtra("song")
            ?: intent.getStringExtra("song_name")
            ?: intent.getStringExtra("singer")
            ?: intent.getStringExtra("artist")
            ?: intent.getStringExtra("name")
            ?: ""

        handleVoiceCommand(context, action, cmd.uppercase(), query, rawText, config.bydXiaoDiAutoForeground)
    }

    private fun handleVoiceCommand(
        context: Context,
        intentAction: String,
        cmd: String,
        query: String,
        rawText: String,
        autoForeground: Boolean
    ) {
        var shouldBringToForeground = autoForeground

        when {
            // 1. 标准下一首
            cmd.contains("NEXT") || rawText.contains("下一首") || rawText.contains("切歌") || rawText.contains("换一首") -> {
                PlaylistManager.skipNext()
            }

            // 2. 标准上一首
            cmd.contains("PREV") || rawText.contains("上一首") -> {
                PlaylistManager.skipPrevious()
            }

            // 3. 暂停 / 停止
            cmd.contains("PAUSE") || cmd.contains("STOP") || rawText.contains("暂停") || rawText.contains("停止") || rawText.contains("别唱了") -> {
                if (PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
                shouldBringToForeground = false
            }

            // 4. 恢复播放 / 播放音乐
            cmd == "PLAY" || cmd == "RESUME" || rawText == "播放音乐" || rawText == "播放" || rawText == "继续播放" || rawText == "放歌" -> {
                if (!PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
            }

            // 5. 语音搜歌、歌名/歌手点播（小迪典型交互：「播放晴天」、「我想听周杰伦」）
            query.isNotEmpty() -> {
                PlaylistManager.searchAndPlay(query, context)
            }

            rawText.isNotEmpty() -> {
                PlaylistManager.searchAndPlay(rawText, context)
            }

            intentAction == Intent.ACTION_MEDIA_BUTTON -> {
                // 媒体按键默认轮转播放
                PlaylistManager.togglePlayPause()
            }

            else -> {
                // 默认唤醒，自动续播
                if (!PlaylistManager.isPlaying.value) {
                    PlaylistManager.togglePlayPause()
                }
            }
        }

        // 借鉴熊猫车机核心体验：当通过小迪唤醒或点歌时，自动拉起千千静听前台大屏
        if (shouldBringToForeground) {
            bringPlayerToFront(context, if (query.isNotEmpty()) query else rawText)
        }
    }

    private fun bringPlayerToFront(context: Context, query: String) {
        try {
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                putExtra("from_byd_voice", true)
                if (query.isNotEmpty()) {
                    putExtra("voice_query", query)
                }
            }
            context.startActivity(appIntent)
        } catch (e: Exception) {
            Log.e(TAG, "拉起千千静听主界面失败: ${e.message}")
        }
    }
}
