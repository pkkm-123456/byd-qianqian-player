cmake_minimum_required(VERSION 3.22.1)
project("car_audio_jni" LANGUAGES C CXX)

find_package(oboe REQUIRED CONFIG)

# 导入 Rust 编译产出的 libqianqian_core.so
add_library(qianqian_core SHARED IMPORTED)
set_target_properties(qianqian_core PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libqianqian_core.so"
)

add_library(car_audio_jni SHARED
    oboe_sink.cpp
)

target_link_libraries(car_audio_jni
    oboe::oboe
    qianqian_core
    android
    log
)
