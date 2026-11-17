@echo off
chcp 65001 >nul
echo ====================================================================
echo 比亚迪宋「千千静听」车载版 一键编译脚本 (Windows)
echo ====================================================================

where cargo >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Rust (cargo)，请先访问 https://rustup.rs 安装 Rust。
    pause
    exit /b 1
)

echo [1/3] 正在检查 Rust Android 交叉编译目标与 cargo-ndk...
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk

echo [2/3] 正在编译 Rust 3D 环绕 DSP 与 NAS 缓存核心库 (arm64-v8a + armeabi-v7a)...
cd rust-core
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../app/src/main/jniLibs build --release
if %errorlevel% neq 0 (
    echo [错误] Rust 核心库编译失败，请确认已设置 ANDROID_NDK_HOME 环境变量。
    cd ..
    pause
    exit /b 1
)
cd ..

echo [3/3] 正在调用 Gradle 打包车机 APK...
if exist gradlew.bat (
    call gradlew.bat :app:assembleDebug
) else (
    gradle :app:assembleDebug
)

echo.
echo [完成] APK 已生成于: app\build\outputs\apk\debug\app-debug.apk
echo 请将该 APK 拷入 FAT32/exFAT U盘，插入比亚迪宋 USB 接口，通过车机「文件管理」直接安装！
pause
