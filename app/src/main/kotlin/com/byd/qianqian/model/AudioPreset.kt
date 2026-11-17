package com.byd.qianqian.model

enum class AudioPreset(
    val id: Int,
    val title: String,
    val subtitle: String,
    val vehicleScope: String,
    val dspFeatures: String
) {
    BYD_STANDARD(
        id = 0,
        title = "原厂 4/6/8 扬声器 (声场重塑)",
        subtitle = "1.8ms 皇帝位 + 1.45x 环绕 + 140Hz 车门防共振",
        vehicleScope = "适配车型：秦PLUS、宋Pro、宋PLUS、元UP/PLUS、海豚、海鸥等",
        dspFeatures = "针对门板喇叭无外置功放特点，开启 1.8ms 主驾声像聚焦、+2.0dB 人声高度补偿，并将 140Hz 以下低频强制收拢至单声道，消除车门塑料共振杂音。"
    ),
    DIRAC_TUNED(
        id = 1,
        title = "Dirac 数字调音系统 (自然微拓宽)",
        subtitle = "0ms 延时直通 + 1.20x 适度声场 + 120Hz 单声道",
        vehicleScope = "适配车型：宋L、海豹后驱版、元PLUS Dirac 调音版",
        dspFeatures = "车机底层已配备 Dirac 冲激响应校正。完全旁路软件延时避免滤波打架，辅以 1.2x 微拓宽与极低失真限幅，维持原车平直通透感。"
    ),
    DYNAUDIO_DIRECT(
        id = 2,
        title = "Dynaudio 丹拿 Hi-Fi 原音直通",
        subtitle = "0ms 直通 + 1.05x 纯净自然 + 全频立体声保真",
        vehicleScope = "适配车型：汉EV/DM-i、唐DM-p、海豹四驱、护卫舰07 (丹拿定制版)",
        dspFeatures = "丹拿搭载 12/16/22 独立 DSP 功放与丝膜高音。关闭所有人工延时，全权交由原车丹拿功放硬件处理主驾声场，保留原汁原味高解析度与超大动态。"
    ),
    DYNAUDIO_CONCERT(
        id = 3,
        title = "Dynaudio 丹拿 3D 环绕音乐厅",
        subtitle = "0ms 延时 + 1.30x 宏大空间声场 + 100Hz 低频下潜",
        vehicleScope = "适配车型：汉/唐/海豹 高配丹拿音响",
        dspFeatures = "激发全车 12/16 个丹拿扬声器包围感。增强车厢纵深混响，让交响乐与演唱会现场宛如置身维也纳金色大厅。"
    ),
    DEVIALET_SAM(
        id = 4,
        title = "Devialet 腾势帝瓦雷 SAM 系统",
        subtitle = "0ms 延时 + 1.15x 声场 + 80Hz 极低频单声道释放",
        vehicleScope = "适配车型：腾势 N7、腾势 N8、腾势 D9 顶配、腾势 Z9GT",
        dspFeatures = "匹配法国帝瓦雷独家 SAM 扬声器主动匹配技术。将低频分频点下潜至 80Hz，释放帝瓦雷重低音炮强劲澎湃能量，瞬态打击力拉满。"
    ),
    STUDIO_PURE(
        id = 5,
        title = "纯净监听直通 (Studio Master)",
        subtitle = "100% 旁路任何声场修饰，仅保留音柱与导航压音",
        vehicleScope = "适配车型：仰望 U8/U7 证据系列 / 高端外置发烧功放改装 / 无损直白玩家",
        dspFeatures = "零音染、零延时、零均衡调节。纯数字比特流直通声卡，同时完整保留千千静听 32 段经典音柱频谱跳动与高德导航平滑压音。"
    );

    companion object {
        fun fromId(id: Int): AudioPreset = entries.firstOrNull { it.id == id } ?: BYD_STANDARD
    }
}
