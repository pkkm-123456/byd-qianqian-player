package com.byd.qianqian.model

data class VoiceWakeConfig(
    val enabled: Boolean = true,
    val wakeWordText: String = "你好千千",
    val sensitivity: Float = 0.65f,
    val hasCustomVoiceTemplate: Boolean = false,
    val recordedTakes: Int = 0
)
