package com.byd.qianqian.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "千千静听车载专集",
    val path: String,
    val durationMs: Long = 0L,
    val isNas: Boolean = false,
    val isPinned: Boolean = false
)
