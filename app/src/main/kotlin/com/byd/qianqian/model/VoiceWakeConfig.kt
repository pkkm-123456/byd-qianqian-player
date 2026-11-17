package com.byd.qianqian.model

data class VoiceWakeConfig(
    val enabled: Boolean = true,
    val wakeWordText: String = "你好千千",
    val sensitivity: Float = 0.65f,
    val hasCustomVoiceTemplate: Boolean = false,
    val recordedTakes: Int = 0,
    /** 比亚迪小迪语音唤醒/指令识别时，是否自动唤起千千静听主界面显示旋转大屏与经典频谱 */
    val bydXiaoDiAutoForeground: Boolean = true,
    /** 借鉴熊猫车机模式：通过比亚迪私有广播、无障碍服务深度接管原车默认播放器与系统小迪播控 */
    val bydXiaoDiHijackDefault: Boolean = true,
    /** 车辆上电 ACC ON 时自动恢复熄火前的播放进度与曲目 */
    val autoResumeOnBoot: Boolean = true
)
