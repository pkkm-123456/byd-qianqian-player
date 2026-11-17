#!/usr/bin/env bash
set -euo pipefail

echo "=== [1/3] 使用 cargo-ndk 编译 Rust 核心库 (arm64-v8a & armeabi-v7a) ==="
cd rust-core
cargo ndk -t arm64-v8a -t armeabi-v7a -o ../app/src/main/jniLibs build --release
cd ..

echo "=== [2/3] 编译 Android 车机 APK (包含 Oboe C++ 与 Jetpack Compose 分屏 UI) ==="
./gradlew :app:assembleRelease

echo "=== [3/3] 构建完成！可将 app/build/outputs/apk/release/app-release.apk 拷入 U盘 插入比亚迪宋安装 ==="
