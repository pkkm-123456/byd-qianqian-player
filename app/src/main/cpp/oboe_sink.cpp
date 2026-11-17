#include <jni.h>
#include <oboe/Oboe.h>
#include <cmath>
#include <memory>
#include <string>

// 声明由 Rust (libqianqian_core.so) 导出的 C ABI 实时函数
extern "C" {
    void rust_engine_init(int32_t sample_rate, float driver_delay_ms, float stereo_width,
                          const char* cache_dir, const char* db_path, int64_t max_cache_bytes);
    void rust_engine_set_playing(bool playing);
    void rust_engine_seek_ms(int64_t pos_ms);
    void rust_engine_skip_next();
    void rust_engine_skip_prev();
    void rust_engine_set_ducking(float target_gain, int32_t ramp_ms);
    void rust_engine_render_audio(float* out_interleaved_stereo, int32_t num_frames);
    void rust_engine_pull_spectrum32(float* out_bars32, float* out_peaks32);
    void rust_engine_pull_spectrum_dyn(float* out_bars, float* out_peaks, int32_t count);
    int64_t rust_engine_get_position_ms();
    void rust_engine_update_quota(int64_t max_bytes);
    void rust_engine_shutdown();
}

class CarOboeEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    bool start(int32_t sampleRate) {
        sampleRate_ = sampleRate;
        return openStream();
    }

    void setPlaying(bool playing) {
        rust_engine_set_playing(playing);
        if (!stream_) return;
        if (playing) {
            stream_->requestStart();
        } else {
            stream_->requestPause();
        }
    }

    void stop() {
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* /*audioStream*/,
            void* audioData,
            int32_t numFrames) override {
        auto* floatOut = static_cast<float*>(audioData);
        // 直接调用 Rust 无锁解码 + 3D M/S 环绕 + 主驾 1.8ms 延时 + PFFFT 频谱旁路
        rust_engine_render_audio(floatOut, numFrames);
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) override {
        if (error == oboe::Result::ErrorDisconnected) {
            openStream();
        }
    }

private:
    bool openStream() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setFormat(oboe::AudioFormat::Float)
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(sampleRate_)
               ->setUsage(oboe::Usage::Media)
               ->setContentType(oboe::ContentType::Music)
               ->setDataCallback(this)
               ->setErrorCallback(this);
        oboe::Result res = builder.openStream(stream_);
        return res == oboe::Result::OK;
    }

    int32_t sampleRate_ = 48000;
    std::shared_ptr<oboe::AudioStream> stream_;
};

static std::unique_ptr<CarOboeEngine> gOboeEngine;

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_initEngine(
        JNIEnv* env, jobject /*thiz*/,
        jint sampleRate, jfloat driverDelayMs, jfloat stereoWidth,
        jstring cacheDir, jstring dbPath, jlong maxCacheBytes) {
    const char* cCache = env->GetStringUTFChars(cacheDir, nullptr);
    const char* cDb = env->GetStringUTFChars(dbPath, nullptr);

    rust_engine_init(sampleRate, driverDelayMs, stereoWidth, cCache, cDb, maxCacheBytes);

    env->ReleaseStringUTFChars(cacheDir, cCache);
    env->ReleaseStringUTFChars(dbPath, cDb);

    gOboeEngine = std::make_unique<CarOboeEngine>();
    gOboeEngine->start(sampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_setPlaybackState(
        JNIEnv* /*env*/, jobject /*thiz*/, jboolean playing) {
    if (gOboeEngine) {
        gOboeEngine->setPlaying(playing);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_seekToMs(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong positionMs) {
    rust_engine_seek_ms(positionMs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_skipNext(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    rust_engine_skip_next();
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_skipPrevious(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    rust_engine_skip_prev();
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_setDuckingGain(
        JNIEnv* /*env*/, jobject /*thiz*/, jfloat targetGain, jint rampMs) {
    rust_engine_set_ducking(targetGain, rampMs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_pullSpectrum32(
        JNIEnv* env, jobject /*thiz*/, jfloatArray outBars32, jfloatArray outPeaks32) {
    jfloat* barsPtr = env->GetFloatArrayElements(outBars32, nullptr);
    jfloat* peaksPtr = env->GetFloatArrayElements(outPeaks32, nullptr);
    rust_engine_pull_spectrum32(barsPtr, peaksPtr);
    env->ReleaseFloatArrayElements(outBars32, barsPtr, 0);
    env->ReleaseFloatArrayElements(outPeaks32, peaksPtr, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_pullSpectrum(
        JNIEnv* env, jobject /*thiz*/, jfloatArray outBars, jfloatArray outPeaks) {
    jsize len = env->GetArrayLength(outBars);
    jfloat* barsPtr = env->GetFloatArrayElements(outBars, nullptr);
    jfloat* peaksPtr = env->GetFloatArrayElements(outPeaks, nullptr);
    rust_engine_pull_spectrum_dyn(barsPtr, peaksPtr, len);
    env->ReleaseFloatArrayElements(outBars, barsPtr, 0);
    env->ReleaseFloatArrayElements(outPeaks, peaksPtr, 0);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_getCurrentPositionMs(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    return rust_engine_get_position_ms();
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_updateCacheQuotaBytes(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong maxBytes) {
    rust_engine_update_quota(maxBytes);
}

extern "C" JNIEXPORT void JNICALL
Java_com_byd_qianqian_bridge_NativeAudioBridge_shutdownEngine(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gOboeEngine) {
        gOboeEngine->stop();
        gOboeEngine.reset();
    }
    rust_engine_shutdown();
}
