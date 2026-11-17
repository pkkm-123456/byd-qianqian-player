# 比亚迪「千千静听」车规级 Hi-Fi 播放器 (BYD Qianqian Player)

<div align="center">
  <img src="https://img.shields.io/badge/Platform-BYD%20DiLink%202.0%2F3.0%2F4.0%2F5.0-blue?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Audio%20Engine-Rust%20%2B%20Google%20Oboe-orange?style=for-the-badge&logo=rust" />
  <img src="https://img.shields.io/badge/Voice%20Integration-BYD%20XiaoDi%20%2B%20Panda%20Car%20Hook-green?style=for-the-badge" />
  <img src="https://img.shields.io/badge/NAS-Feiniu%20fnOS%20WebDAV-red?style=for-the-badge" />
</div>

<br/>

专为**比亚迪 (BYD) 全系车型（宋 / 汉 / 唐 / 秦 / 元 / 海豹 / 海豚 / 腾势 / 仰望 / 方程豹）**定制开发的**车规级经典千千静听音乐播放器**。

不仅 1:1 复刻 2000 年代 Windows 经典千千静听 5.x 拟物化金属质感面板与 60fps 硬件 FFT 频谱跳动，更全面融合**「熊猫车机」底层架构**：原生深度接管**「你好小迪」系统级语音唤醒与指令**、方向盘媒体按键、仪表盘元数据同步与高德地图导航动态压音！

---

## 🌟 核心特性亮点

### 1. 🐼 借鉴「熊猫车机」深度接管比亚迪「小迪」语音
- **系统广播深度监听**：实时拦截 `com.byd.voice.action.MUSIC_CONTROL` 与 `com.byd.dilink.action.VOICE_COMMAND`。
- **免按键语音点歌**：直接对车机说：
  - *“你好小迪，播放音乐”* —— 自动唤起千千静听并续播歌曲；
  - *“你好小迪，播放周杰伦的晴天”* —— 智能提取曲名与歌手，毫秒级定位播放；
  - *“你好小迪，下一首 / 上一首 / 暂停 / 继续播放”* —— 方向盘与语音无缝联动；
- **点歌自动唤起千千静听大屏**：通过小迪点歌时，车机中控自适应旋转屏自动弹出千千静听经典主界面，实时展示复古金属面板与跳动的全彩动态频谱。
- **原厂默认播放器接管（熊猫车机独家无障碍服务）**：针对部分 DiLink 版本硬编码“播放音乐”强制启动原厂酷我/酷狗音乐的问题，内置无障碍服务静默拦截并秒级切换回千千静听！

### 2. 🎛 1:1 像素级复刻千千静听 5.x 经典界面
- **金属拉丝深蓝面板**：立体倒角边缘、经典螺丝铆钉、精致音量推子与金属旋钮。
- **高对比度荧光绿/蓝 LCD 液晶屏**：点阵滚字曲名、KBPS 码率、KHZ 采样率、STEREO/SURROUND 指示灯。
- **60fps 原生 FFT 频谱与峰值帽**：32/48 段高密度多色阶频谱柱（深绿 - 黄 - 橙 - 烈焰红），带重力加速度物理下落峰值帽（Peak Cap）。
- **旋转屏与分屏自适应**：完美适配比亚迪中控横屏（16:9）、竖屏（9:16）自动旋转以及与高德地图 1:3、1:2、2:3 分屏模式。

### 3. 🚗 车规级音频架构与丹拿/帝瓦雷定制 DSP
- **底层 Rust + Google Oboe**：基于 OpenSL ES / AAudio 底层直出，绕过 Android AudioFlinger 有损重采样，实现真正的 192kHz/24bit Hi-Res 源码输出。
- **主驾“皇帝位”声场延时补偿**：左声道精准延时 1.8ms，抵消左前扬声器距离主驾驶位过近的时间差，让立体声音场正对车主眉心。
- **原厂音响深度调校预设**：
  - `原厂 6/8 扬声器增强`（弥补原车低频松散与高频发闷）；
  - `丹拿 Hi-Fi 纯净直通`（关闭虚拟环绕，释放丹拿高解析真实还原力）；
  - `丹拿音乐厅全景声`（拓宽 3D M/S 空间环绕感）；
  - `腾势帝瓦雷震撼低音`（60Hz 以下超重低音下潜增强）；
- **车规 AudioFocus 智能压音**：高德地图播报导航时音乐平滑渐变压低至 20%，雷达报警与倒车影像自动静音。

### 4. 🗄 飞牛 NAS (fnOS) WebDAV 无缝直连与离线盲听
- **车载 4G/5G 远程 WebDAV 直连**：支持局域网 IP 与公网域名（端口 5005 / 5006 HTTPS）。
- **64KB 头部秒读**：仅需拉取前 64KB 即可解析 FLAC/MP3 内嵌封面与 ID3v2 标签，曲库秒级加载。
- **智能 LRU 4GB 高速缓存**：边下边播，自动保存已听歌曲，当本地占用超过 4GB 水位线时自动淘汰早期曲目；进地下车库无网络也能畅享无损音乐。
- **同音拼音与歌词全搜**：集成 Rust SQLite FTS5 引擎，即便车厢胎噪导致小迪识别出错别字，拼音索引依然能秒搜定位。

---

## 📲 APK 下载与车机安装方法

### 方法一：直接从 GitHub Releases 下载（推荐）
1. 访问本仓库的 **[Releases 发布页面](https://github.com/pkkm-123456/byd-qianqian-player/releases)**。
2. 下载最新发布的 `byd-qianqian-player-v1.0.0.apk`。
3. 复制到 FAT32/exFAT 格式的 U 盘中，插入比亚迪中央扶手箱或副仪表台的 USB 接口。
4. 打开比亚迪车机自带的 **「文件管理」** -> 选择 **「U盘」** -> 点击 APK 安装。

### 方法二：通过 GitHub Actions 自动编译构建
本项目已配置全自动 GitHub Actions CI/CD 流水线：
每次代码更新都会自动在云端编译最新 Rust 原生核心并打包 APK，可前往 **[Actions 页面](https://github.com/pkkm-123456/byd-qianqian-player/actions)** 下载 `byd-qianqian-player-v1.0.0` Artifact。

---

## ⚙️ 比亚迪车机专属配置建议

### 1. 开启小迪语音免按键接管
1. 打开播放器右上角齿轮 -> 进入 **「语音唤醒与播控设置」**。
2. 确保勾选 **「小迪语音指令深度接管（熊猫车机模式）」** 与 **「小迪点歌时自动打开千千静听大屏」**。
3. 随时对车机说：*“你好小迪，播放晴天”*，即可体验丝滑点歌！

### 2. 启用无障碍接管（针对原车强开酷我/酷狗的系统）
若您的车机版本呼叫“播放音乐”会被强制分流至原车自带播放器：
1. 点击设置界面的 **「🛠 启用无障碍接管服务」**。
2. 在车机系统辅助功能列表中找到 **「千千静听车载版」** 并点击开启。
3. 之后原车播放器启动时将被千千静听瞬间静音并切回前台。

### 3. 配置飞牛 NAS
1. 进入 **「飞牛私有云 NAS 设置」**。
2. 填写 WebDAV 服务器地址（例如 `http://192.168.1.100:5005` 或公网域名）、账号、密码及音乐共享文件夹路径。
3. 点击 **「测试连通性」**，通过后点击 **「保存并同步曲库」** 即可。

---

## 🛠 项目架构

```
byd-qianqian-player/
├── app/                                # Android 车载应用程序
│   ├── src/main/cpp/                   # C++ Oboe 低延迟音频输出驱动 (oboe_sink.cpp)
│   ├── src/main/kotlin/com/byd/qianqian/
│   │   ├── bridge/NativeAudioBridge.kt # Kotlin 与 Rust/C++ 零拷贝桥接
│   │   ├── manager/PlaylistManager.kt  # 播放列表、本地/USB/NAS 曲目与智能检索
│   │   ├── service/CarPlaybackService.kt# AndroidX Media3 车载后台常驻服务
│   │   ├── voice/BydXiaoDiReceiver.kt  # 比亚迪小迪语音广播接收器（熊猫车机模式）
│   │   ├── voice/BydXiaoDiAccessibilityService.kt # 无障碍默认播放器接管服务
│   │   └── ui/                         # Jetpack Compose 1:1 复刻千千经典界面与频谱
├── rust-core/                          # 高性能 Rust 核心引擎
│   ├── src/decoder.rs                  # Symphonia 多格式无损软解 (FLAC/APE/WAV/MP3)
│   ├── src/dsp.rs                      # 丹拿/帝瓦雷音效、主驾 1.8ms 延时、3D 环绕
│   ├── src/lyrics_db/                  # SQLite FTS5 汉字+无声调全拼双模容错搜歌
│   └── src/nas_cache/                  # 飞牛 WebDAV 分块边下边播与 4GB LRU 缓存淘汰
└── .github/workflows/build-apk.yml     # 自动化云端编译构建发布工作流
```

---

## 📄 开源许可证

本项目基于 [Apache-2.0 License](LICENSE) 协议开源。
致敬为无数人带来音乐感动的 **千千静听 (TTPlayer)** 与服务广大比亚迪车友的 **熊猫车机** 开发者！
