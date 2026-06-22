#include <android/log.h>
#include <algorithm>
#include <dlfcn.h>
#include <jni.h>
#include <mutex>
#include <string>
#include <unistd.h>

#include "ggml-vulkan.h"
#include "whisper.h"

#define ASRM_LOG_TAG "ASRMWhisperJNI"

namespace {

bool has_vulkan_loader() {
    void* handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        return false;
    }
    dlclose(handle);
    return true;
}

std::string sanitize_segment_text(const char* raw) {
    std::string text = raw == nullptr ? "" : raw;
    for (char& ch : text) {
        if (ch == '\t' || ch == '\n' || ch == '\r') {
            ch = ' ';
        }
    }
    const auto first = text.find_first_not_of(" ");
    if (first == std::string::npos) {
        return "";
    }
    const auto last = text.find_last_not_of(" ");
    return text.substr(first, last - first + 1);
}

int worker_threads() {
    const long processors = sysconf(_SC_NPROCESSORS_ONLN);
    if (processors <= 0) {
        return 2;
    }
    return std::max(1, std::min(4, static_cast<int>(processors)));
}

struct WhisperSession {
    explicit WhisperSession(whisper_context* context) : context(context) {}

    whisper_context* context;
    std::mutex mutex;
};

WhisperSession* to_session(jlong handle) {
    return reinterpret_cast<WhisperSession*>(handle);
}

std::string transcribe_with_context(JNIEnv* env, whisper_context* context, jfloatArray pcm16k_mono) {
    if (context == nullptr || pcm16k_mono == nullptr) {
        return "";
    }

    const jsize sample_count = env->GetArrayLength(pcm16k_mono);
    if (sample_count <= 0) {
        return "";
    }

    jboolean is_copy = JNI_FALSE;
    jfloat* samples = env->GetFloatArrayElements(pcm16k_mono, &is_copy);
    if (samples == nullptr) {
        return "";
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = worker_threads();
    params.language = "ja";
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    const int result = whisper_full(context, params, samples, sample_count);
    env->ReleaseFloatArrayElements(pcm16k_mono, samples, JNI_ABORT);

    std::string output;
    if (result == 0) {
        const int segment_count = whisper_full_n_segments(context);
        for (int index = 0; index < segment_count; ++index) {
            const std::string text = sanitize_segment_text(whisper_full_get_segment_text(context, index));
            if (text.empty()) {
                continue;
            }
            const int64_t start_ms = whisper_full_get_segment_t0(context, index) * 10;
            const int64_t end_ms = whisper_full_get_segment_t1(context, index) * 10;
            output += std::to_string(start_ms);
            output += '\t';
            output += std::to_string(std::max<int64_t>(end_ms, start_ms + 250));
            output += '\t';
            output += text;
            output += '\n';
        }
    } else {
        __android_log_print(ANDROID_LOG_ERROR, ASRM_LOG_TAG, "whisper_full failed: %d", result);
    }
    return output;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeSmokeTest(
    JNIEnv* /* env */,
    jclass /* clazz */) {
    __android_log_print(
        ANDROID_LOG_DEBUG,
        ASRM_LOG_TAG,
        "native smoke test ok, whisper.cpp %s",
        whisper_version());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeIsVulkanCapable(
    JNIEnv* /* env */,
    jclass /* clazz */) {
    if (!has_vulkan_loader()) {
        return JNI_FALSE;
    }
    try {
        return ggml_backend_vk_get_device_count() > 0 ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        __android_log_print(
            ANDROID_LOG_WARN,
            ASRM_LOG_TAG,
            "Vulkan capability check failed: %s",
            error.what());
        return JNI_FALSE;
    } catch (...) {
        __android_log_print(ANDROID_LOG_WARN, ASRM_LOG_TAG, "Vulkan capability check failed");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeCreateSession(
    JNIEnv* env,
    jclass /* clazz */,
    jstring model_path) {
    if (model_path == nullptr) {
        return 0L;
    }

    const char* model_path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (model_path_chars == nullptr) {
        return 0L;
    }

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = true;

    whisper_context* context = whisper_init_from_file_with_params(model_path_chars, context_params);
    env->ReleaseStringUTFChars(model_path, model_path_chars);
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, ASRM_LOG_TAG, "failed to initialize whisper session");
        return 0L;
    }

    return reinterpret_cast<jlong>(new WhisperSession(context));
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeReleaseSession(
    JNIEnv* /* env */,
    jclass /* clazz */,
    jlong session_handle) {
    WhisperSession* session = to_session(session_handle);
    if (session == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> guard(session->mutex);
        whisper_free(session->context);
        session->context = nullptr;
    }
    delete session;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeTranscribeSession(
    JNIEnv* env,
    jclass /* clazz */,
    jlong session_handle,
    jfloatArray pcm16k_mono) {
    WhisperSession* session = to_session(session_handle);
    if (session == nullptr || session->context == nullptr) {
        return env->NewStringUTF("");
    }
    std::lock_guard<std::mutex> guard(session->mutex);
    const std::string output = transcribe_with_context(env, session->context, pcm16k_mono);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_summerdez_asmrplayer_data_ai_NativeWhisperRuntime_nativeTranscribeJapanese(
    JNIEnv* env,
    jclass /* clazz */,
    jstring model_path,
    jfloatArray pcm16k_mono) {
    if (model_path == nullptr || pcm16k_mono == nullptr) {
        return env->NewStringUTF("");
    }

    const char* model_path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (model_path_chars == nullptr) {
        return env->NewStringUTF("");
    }

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = true;

    whisper_context* context = whisper_init_from_file_with_params(model_path_chars, context_params);
    env->ReleaseStringUTFChars(model_path, model_path_chars);
    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, ASRM_LOG_TAG, "failed to initialize whisper context");
        return env->NewStringUTF("");
    }

    const std::string output = transcribe_with_context(env, context, pcm16k_mono);
    whisper_free(context);
    return env->NewStringUTF(output.c_str());
}
