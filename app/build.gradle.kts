plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.byd.qianqian"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byd.qianqian"
        minSdk = 26 // 兼容比亚迪 DiLink 2.0 (Android 8/9) 与 DiLink 3.0/4.0/5.0
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-car"

        ndk {
            // 同时支持 64位 与 老款 DiLink 32位 车机系统
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-O3", "-ffast-math", "-Wall")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        prefab = true // 用于引入 Google Oboe 原生库
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    // Media3: 接管比亚迪小迪语音播控与方向盘切歌按键
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Google Oboe 低延迟 C++ 音频输出
    implementation("com.google.oboe:oboe:1.8.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
