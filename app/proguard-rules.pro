# =========================================================================
# 比亚迪「千千静听」车机版 Proguard 混淆与防剥离规则
# =========================================================================

# 1. 保护 JNI 原生桥接类与 Native 方法签名（防 UnsatisfiedLinkError 崩溃）
-keep class com.byd.qianqian.bridge.NativeAudioBridge {
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. 保护数据模型（用于序列化与反射）
-keep class com.byd.qianqian.model.** { *; }

# 3. 保护 AndroidX Media3 与 ExoPlayer 服务及媒体按键回调
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# 4. 保持 Compose Runtime 规则
-keep class androidx.compose.runtime.** { *; }

# 5. 保持 Kotlin 协程与原子操作类
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
