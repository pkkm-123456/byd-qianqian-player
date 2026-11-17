# 比亚迪「千千静听」车载 Hi-Fi 音乐播放器 (BYD Qianqian Player)

[![Build BYD Car Qianqian APK](https://github.com/your-username/byd-qianqian-player/actions/workflows/build-apk.yml/badge.svg)](.github/workflows/build-apk.yml)
[![Platform](https://img.shields.io/badge/Platform-BYD%20DiLink%20(Android%208.0~14)-blue.svg)]()
[![Rust Core](https://img.shields.io/badge/Rust-2021%20(Symphonia%20%2B%20DSP)-orange.svg)]()
[![Audio Output](https://img.shields.io/badge/Audio-Google%20Oboe%20(Float%20PCM)-green.svg)]()

专为**比亚迪（BYD）王朝/海洋系列（宋、秦、汉、唐、元、海豹、海豚等）车机系统（DiLink 2.0 / 3.0 / 4.0 / 5.0）**深度定制的开源音乐播放器。结合千千静听经典 32 段跳动音柱与现代车规级音频 DSP 算法，打通本地存储、U盘直读与飞牛 fnOS NAS WebDAV 远程无损直存。

---

## 🌟 核心特性

1. **经典千千静听 32 段音柱与自由落体峰值帽**
   - 汉宁窗 (Hanning Window) 1024 点 FFT 实时分析，对数划分 20Hz ~ 18kHz 频段。
   - 忠实还原千千静听分节横格线与顶部 Peak Hold 白帽阻尼自由落体。
   - 针对车机低功耗优化，30fps 零分配高效重绘，保护车机芯片防发热。

2. **车规级专用 DSP 声音增强与多品牌音响预设匹配**
   - **比亚迪全车系音响硬件品类专属预设（杜绝梳状滤波对冲）**：
     - **原厂 4/6/8 扬声器 (秦PLUS/宋Pro/宋PLUS/海豚/海鸥/元等)**：开启 1.8ms 主驾皇帝位延时聚焦 + 1.45x 环绕拓宽 + 140Hz 车门低频防共振 + 2.0dB 人声高度补偿。
     - **Dirac 数字调音系统 (宋L/海豹后驱/元PLUS)**：0ms 延时直通（避免与车机 Dirac 冲激响应滤波打架） + 1.20x 适度自然微拓宽。
     - **Dynaudio 丹拿 Hi-Fi 原音直通 (汉/唐/海豹四驱/护卫舰07)**：0ms 延时直通 + 1.05x 纯净微拓宽 + 全频立体声保真，全权交由原车丹拿 12/16/22 独立功放分频，释放大动态与高解析。
     - **Dynaudio 丹拿 3D 环绕音乐厅**：0ms 延时 + 1.30x 开阔宏大包围感声场 + 100Hz 低频延伸。
     - **Devialet 腾势帝瓦雷 SAM 系统 (腾势 N7/N8/D9/Z9GT)**：0ms 延时 + 1.15x 声场 + 80Hz 极低频单声道，充分释放帝瓦雷重低音炮澎湃下潜。
     - **纯净监听直通 (仰望 U8/U7 证据音响 / 母带发烧直出)**：100% 旁路任何声场修饰，仅保留音柱与导航压音。
   - **低频单声道锚定**：抑制车门共振与杂音，让低音紧实有质感。
   - **高德地图导航平滑压音 (Ducking)**：导航播报时音乐平滑降至 20%，播报完毕 300ms 渐变回弹，不突兀。

3. **Rust 多格式无损解码引擎 + 无锁环形缓冲**
   - 基于 `symphonia` 提供 FLAC、APE、WAV、MP3、AAC、OGG 全格式无损解码。
   - 后台解码线程与 Oboe 实时音频渲染线程采用无锁 SPSC (单生产者单消费者) 环形缓冲区，杜绝播放卡顿与爆音。

4. **飞牛 fnOS (WebDAV) 直存与车载智能 LRU 缓存**
   - 512KB HTTP Range 分块流式拉取，进出地下车库断网免破损。
   - 本地容量配额动态调节（1GB ~ 32GB），超配额时按最早播放时间淘汰音频文件至 85% 水位线。
   - 淘汰音频时**永久保留 SQLite 拼音索引**，断网也能秒搜歌名与歌词。
   - 收藏曲目自动归档至飞牛 NAS 目标目录并标记免清理。

5. **车机旋转大屏与高德分屏自适应**
   - 全屏横屏：经典三栏（曲目信息 + 32段音柱 + 双色高亮同步歌词）。
   - 1/2 对半分屏 / 比亚迪 90° 旋转竖屏：上下紧凑双栏布局。
   - 1/3 导航分屏模式：专为高德地图伴侣设计，超大字体与防误触盲操大按钮。
   - 系统级悬浮歌词与车载昼夜深浅色主题自适应（联动大灯）。

6. **方向盘按键与仪表盘 MediaSession 对接**
   - 完美对接车身 CAN 总线方向盘左侧切歌滚轮（上一首/下一首/暂停）。
   - 实时推送曲目元数据与播放进度至车机仪表盘与 HUD 抬头显示。
   - 兼容 Android 14 前台媒体服务合规常驻通知栏。

7. **端侧离线语音唤醒词与车主专属声纹录制向导**
   - **内置默认唤醒词**：「你好千千」。
   - **完全自由自定义**：可自由修改为任意 3~5 字短语（如「千千同学」、「小千听歌」、「播放音乐」）。
   - **3遍声纹录制向导**：支持车主口述录制 3 遍生成专属离线声学模板，精准抗击车内风噪胎噪误触。
   - **比亚迪双模兼容**：呼叫原车「你好小迪」依然通过车身系统广播秒级控制播放器；呼叫「你好千千」可享受无缝压音回弹与拼音容错点歌。

---

## 🏗️ 架构概览

```
byd-qianqian-player/
├── rust-core/                    # Rust 核心引擎 (编译为 libqianqian_core.so)
│   ├── src/
│   │   ├── decoder.rs            # Symphonia 多格式解码与无锁 SPSC 环形缓冲
│   │   ├── dsp/                  # 车规 DSP (M/S 环绕、主驾延时、低频防共振、32段FFT)
│   │   ├── lyrics_db/            # SQLite FTS5 汉字+无声调拼音双模容错检索
│   │   ├── nas_cache/            # 飞牛 WebDAV 分块下载与 LRU 水位线淘汰
│   │   └── lib.rs                # C ABI 与 JNI 跨语言桥接导出
├── app/                          # Android 车机应用工程
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt    # 原生 NDK 构建
│   │   │   └── oboe_sink.cpp     # Google Oboe 低延迟 Float 音频渲染
│   │   ├── kotlin/com/byd/qianqian/
│   │   │   ├── bridge/           # NativeAudioBridge 原生调用层
│   │   │   ├── manager/          # PlaylistManager 播放列表与U盘扫描
│   │   │   ├── service/          # CarPlaybackService 车载媒体前台服务
│   │   │   └── ui/               # Jetpack Compose 自适应车载界面
└── build_car_apk_windows.bat     # Windows 一键编译打包脚本
```

---

## 🛠️ 本地编译与打包

### 前置环境需求
1. **JDK 17**（推荐 Eclipse Temurin 17）
2. **Android SDK & NDK**（推荐 NDK r26b / 26.1.10909125，配置环境变量 `ANDROID_NDK_HOME`）
3. **Rust**（通过 [rustup.rs](https://rustup.rs) 安装）
   - 安装 Android 交叉编译目标：
     ```bash
     rustup target add aarch64-linux-android armv7-linux-androideabi
     cargo install cargo-ndk
     ```

### 方式一：Windows 一键打包 (推荐)
直接双击运行项目根目录下的：
```bat
build_car_apk_windows.bat
```
脚本将自动完成：
1. 编译 Rust 核心动态库（`arm64-v8a` + `armeabi-v7a`）。
2. 调用 Gradle Wrapper 打包出完整的车机版 APK：`app/build/outputs/apk/debug/app-debug.apk`。

### 方式二：Linux / macOS 终端编译
```bash
chmod +x build_car_apk.sh
./build_car_apk.sh
```

---

## 🚗 安装至比亚迪车机

1. 准备一个 **FAT32 或 exFAT** 格式的普通 U 盘。
2. 将生成的 `app-debug.apk`（或 `app-release.apk`）复制到 U 盘根目录。
3. 插入比亚迪扶手箱内的 USB 多媒体数据接口。
4. 打开车机桌面的**「文件管理」**应用，找到 U 盘中的 APK 点击安装即可。
5. （可选）将你喜爱的无损歌曲（`.flac`, `.mp3`, `.wav` 等）及同名 `.lrc` 歌词放入 U 盘 `Music` 文件夹，进入播放器点击「歌单」->「扫描U盘/存储」即可秒级导入！

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源发布。
