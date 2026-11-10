#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "libretro.h"

#define LOG_TAG "RetroboyLibretro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct CoreApi {
    decltype(&retro_api_version) api_version = nullptr;
    decltype(&retro_set_environment) set_environment = nullptr;
    decltype(&retro_set_video_refresh) set_video_refresh = nullptr;
    decltype(&retro_set_audio_sample) set_audio_sample = nullptr;
    decltype(&retro_set_audio_sample_batch) set_audio_sample_batch = nullptr;
    decltype(&retro_set_input_poll) set_input_poll = nullptr;
    decltype(&retro_set_input_state) set_input_state = nullptr;
    decltype(&retro_init) init = nullptr;
    decltype(&retro_deinit) deinit = nullptr;
    decltype(&retro_get_system_info) get_system_info = nullptr;
    decltype(&retro_get_system_av_info) get_system_av_info = nullptr;
    decltype(&retro_load_game) load_game = nullptr;
    decltype(&retro_unload_game) unload_game = nullptr;
    decltype(&retro_run) run = nullptr;
    decltype(&retro_reset) reset = nullptr;
    decltype(&retro_serialize_size) serialize_size = nullptr;
    decltype(&retro_serialize) serialize = nullptr;
    decltype(&retro_unserialize) unserialize = nullptr;
    decltype(&retro_set_controller_port_device) set_controller_port_device = nullptr;
};

struct Runtime {
    void* handle = nullptr;
    ANativeWindow* window = nullptr;
    CoreApi api;
    std::thread worker;
    std::atomic<bool> running{false};
    std::atomic<bool> paused{false};
    std::atomic<uint16_t> inputMask{0};
    std::mutex coreMutex;
    std::mutex audioMutex;
    static constexpr size_t AUDIO_CAPACITY = 65536;
    std::array<int16_t, AUDIO_CAPACITY> audioRing{};
    size_t audioRead = 0;
    size_t audioWrite = 0;
    size_t audioCount = 0;
    std::string systemDir;
    std::string saveDir;
    std::string contentDir;
    std::string gamePath;
    retro_pixel_format pixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;
    unsigned frameWidth = 320;
    unsigned frameHeight = 240;
    unsigned windowWidth = 0;
    unsigned windowHeight = 0;
    double fps = 60.0;
    double sampleRate = 48000.0;
    std::vector<uint8_t> lastFrame;
    unsigned lastWidth = 0;
    unsigned lastHeight = 0;
    size_t lastPitch = 0;
    bool initialized = false;
    bool loaded = false;
};

static Runtime* gRuntime = nullptr;

static void coreLog(enum retro_log_level level, const char* format, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    int priority = level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR :
                   level >= RETRO_LOG_WARN ? ANDROID_LOG_WARN :
                   level >= RETRO_LOG_INFO ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG;
    __android_log_print(priority, LOG_TAG, "%s", buffer);
}

static bool environmentCallback(unsigned command, void* data) {
    Runtime* r = gRuntime;
    if (!r) return false;

    switch (command) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *static_cast<bool*>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            if (!data) return false;
            r->pixelFormat = *static_cast<retro_pixel_format*>(data);
            return true;
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data) *static_cast<const char**>(data) = r->systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data) *static_cast<const char**>(data) = r->saveDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CONTENT_DIRECTORY:
            if (data) *static_cast<const char**>(data) = r->contentDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_USERNAME:
            if (data) *static_cast<const char**>(data) = "Retroboy Player";
            return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data) static_cast<retro_log_callback*>(data)->log = coreLog;
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            if (data) *static_cast<unsigned*>(data) = RETRO_LANGUAGE_ENGLISH;
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            if (data) *static_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_MESSAGE:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
        case RETRO_ENVIRONMENT_SET_ROTATION:
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
            return true;
        case RETRO_ENVIRONMENT_GET_VARIABLE:
            return false;
        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            if (!data) return false;
            auto* geometry = static_cast<retro_game_geometry*>(data);
            r->frameWidth = std::max(1u, geometry->base_width);
            r->frameHeight = std::max(1u, geometry->base_height);
            return true;
        }
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            if (!data) return false;
            auto* av = static_cast<retro_system_av_info*>(data);
            if (av->timing.fps > 1.0) r->fps = av->timing.fps;
            if (av->timing.sample_rate > 8000.0) r->sampleRate = av->timing.sample_rate;
            r->frameWidth = std::max(1u, av->geometry.base_width);
            r->frameHeight = std::max(1u, av->geometry.base_height);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE:
            if (data) *static_cast<float*>(data) = static_cast<float>(r->fps);
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            if (data) *static_cast<int*>(data) = RETRO_AV_ENABLE_VIDEO | RETRO_AV_ENABLE_AUDIO;
            return true;
        default:
            return false;
    }
}

static void inputPollCallback() {}

static int16_t inputStateCallback(unsigned port, unsigned device, unsigned, unsigned id) {
    Runtime* r = gRuntime;
    if (!r || port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    uint16_t mask = r->inputMask.load(std::memory_order_relaxed);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(mask);
    if (id < 16) return (mask & (1u << id)) ? 1 : 0;
    return 0;
}

static void pushAudio(const int16_t* samples, size_t frames) {
    Runtime* r = gRuntime;
    if (!r || !samples || frames == 0) return;
    std::lock_guard<std::mutex> lock(r->audioMutex);
    const size_t total = frames * 2;
    const size_t start = total > Runtime::AUDIO_CAPACITY ? total - Runtime::AUDIO_CAPACITY : 0;
    for (size_t i = start; i < total; ++i) {
        if (r->audioCount == Runtime::AUDIO_CAPACITY) {
            r->audioRead = (r->audioRead + 1) % Runtime::AUDIO_CAPACITY;
            --r->audioCount;
        }
        r->audioRing[r->audioWrite] = samples[i];
        r->audioWrite = (r->audioWrite + 1) % Runtime::AUDIO_CAPACITY;
        ++r->audioCount;
    }
}

static void audioSampleCallback(int16_t left, int16_t right) {
    const int16_t samples[2] = {left, right};
    pushAudio(samples, 1);
}

static size_t audioBatchCallback(const int16_t* data, size_t frames) {
    pushAudio(data, frames);
    return frames;
}

static void videoRefreshCallback(const void* data, unsigned width, unsigned height, size_t pitch) {
    Runtime* r = gRuntime;
    if (!r || !r->window || width == 0 || height == 0) return;

    const size_t bpp = r->pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888 ? 4 : 2;
    const size_t rowBytes = static_cast<size_t>(width) * bpp;

    if (data) {
        if (pitch < rowBytes) return;
        r->lastFrame.resize(rowBytes * height);
        const auto* source = static_cast<const uint8_t*>(data);
        for (unsigned y = 0; y < height; ++y) {
            std::memcpy(r->lastFrame.data() + static_cast<size_t>(y) * rowBytes,
                        source + static_cast<size_t>(y) * pitch, rowBytes);
        }
        r->lastWidth = width;
        r->lastHeight = height;
        r->lastPitch = rowBytes;
    } else if (r->lastFrame.empty()) {
        return;
    }

    if (r->windowWidth != width || r->windowHeight != height) {
        ANativeWindow_setBuffersGeometry(r->window, static_cast<int32_t>(width),
                                         static_cast<int32_t>(height), WINDOW_FORMAT_RGBA_8888);
        r->windowWidth = width;
        r->windowHeight = height;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(r->window, &buffer, nullptr) != 0) return;

    const unsigned copyWidth = std::min(width, static_cast<unsigned>(buffer.width));
    const unsigned copyHeight = std::min(height, static_cast<unsigned>(buffer.height));
    for (unsigned y = 0; y < copyHeight; ++y) {
        const uint8_t* src = r->lastFrame.data() + static_cast<size_t>(y) * r->lastPitch;
        uint8_t* dst = static_cast<uint8_t*>(buffer.bits) + static_cast<size_t>(y) * buffer.stride * 4;
        for (unsigned x = 0; x < copyWidth; ++x) {
            uint8_t red = 0, green = 0, blue = 0;
            if (r->pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
                uint32_t pixel;
                std::memcpy(&pixel, src + static_cast<size_t>(x) * 4, 4);
                red = static_cast<uint8_t>((pixel >> 16) & 0xff);
                green = static_cast<uint8_t>((pixel >> 8) & 0xff);
                blue = static_cast<uint8_t>(pixel & 0xff);
            } else {
                uint16_t pixel;
                std::memcpy(&pixel, src + static_cast<size_t>(x) * 2, 2);
                if (r->pixelFormat == RETRO_PIXEL_FORMAT_RGB565) {
                    red = static_cast<uint8_t>(((pixel >> 11) & 0x1f) * 255 / 31);
                    green = static_cast<uint8_t>(((pixel >> 5) & 0x3f) * 255 / 63);
                    blue = static_cast<uint8_t>((pixel & 0x1f) * 255 / 31);
                } else {
                    red = static_cast<uint8_t>(((pixel >> 10) & 0x1f) * 255 / 31);
                    green = static_cast<uint8_t>(((pixel >> 5) & 0x1f) * 255 / 31);
                    blue = static_cast<uint8_t>((pixel & 0x1f) * 255 / 31);
                }
            }
            const size_t offset = static_cast<size_t>(x) * 4;
            dst[offset] = red;
            dst[offset + 1] = green;
            dst[offset + 2] = blue;
            dst[offset + 3] = 0xff;
        }
    }
    ANativeWindow_unlockAndPost(r->window);
}

static bool loadSymbol(void* handle, const char* name, void** output) {
    dlerror();
    *output = dlsym(handle, name);
    return *output != nullptr;
}

static void runLoop(Runtime* r) {
    auto nextFrame = std::chrono::steady_clock::now();
    while (r->running.load()) {
        if (r->paused.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            nextFrame = std::chrono::steady_clock::now();
            continue;
        }
        const auto frameStart = std::chrono::steady_clock::now();
        {
            std::lock_guard<std::mutex> lock(r->coreMutex);
            if (!r->running.load()) break;
            r->api.run();
        }
        const double safeFps = std::clamp(r->fps, 20.0, 240.0);
        const auto frameDuration = std::chrono::duration_cast<std::chrono::steady_clock::duration>(
            std::chrono::duration<double>(1.0 / safeFps));
        nextFrame = std::max(nextFrame + frameDuration, frameStart + frameDuration);
        std::this_thread::sleep_until(nextFrame);
    }
}

static void cleanupRuntime(Runtime* r) {
    if (!r) return;
    r->running.store(false);
    if (r->worker.joinable()) r->worker.join();
    {
        std::lock_guard<std::mutex> lock(r->coreMutex);
        if (r->loaded && r->api.unload_game) r->api.unload_game();
        r->loaded = false;
        if (r->initialized && r->api.deinit) r->api.deinit();
        r->initialized = false;
    }
    if (r->handle) {
        dlclose(r->handle);
        r->handle = nullptr;
    }
    if (r->window) {
        ANativeWindow_release(r->window);
        r->window = nullptr;
    }
}

static std::string fromJava(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jstring resultString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeCreate(
    JNIEnv* env, jobject,
    jstring core_path, jstring content_path,
    jstring system_dir, jstring save_dir,
    jobject surface
) {
    if (gRuntime) {
        cleanupRuntime(gRuntime);
        delete gRuntime;
        gRuntime = nullptr;
    }

    auto* r = new Runtime();
    gRuntime = r;
    const std::string corePath = fromJava(env, core_path);
    r->gamePath = fromJava(env, content_path);
    r->systemDir = fromJava(env, system_dir);
    r->saveDir = fromJava(env, save_dir);
    r->contentDir = r->gamePath.substr(0, r->gamePath.find_last_of('/'));

    if (!surface || corePath.empty() || r->gamePath.empty()) {
        cleanupRuntime(r); delete r; gRuntime = nullptr;
        return resultString(env, "ERRO: caminho do núcleo, jogo ou tela ausente.");
    }

    r->window = ANativeWindow_fromSurface(env, surface);
    if (!r->window) {
        cleanupRuntime(r); delete r; gRuntime = nullptr;
        return resultString(env, "ERRO: não foi possível abrir a superfície de vídeo.");
    }

    r->handle = dlopen(corePath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!r->handle) {
        const char* error = dlerror();
        std::string message = "ERRO ao abrir o núcleo: ";
        message += error ? error : "biblioteca inválida";
        cleanupRuntime(r); delete r; gRuntime = nullptr;
        return resultString(env, message);
    }

#define LOAD_CORE_SYMBOL(member, symbol) \
    if (!loadSymbol(r->handle, symbol, reinterpret_cast<void**>(&r->api.member))) { \
        std::string message = "ERRO: núcleo sem função obrigatória " + std::string(symbol); \
        cleanupRuntime(r); delete r; gRuntime = nullptr; return resultString(env, message); \
    }

    LOAD_CORE_SYMBOL(api_version, "retro_api_version");
    LOAD_CORE_SYMBOL(set_environment, "retro_set_environment");
    LOAD_CORE_SYMBOL(set_video_refresh, "retro_set_video_refresh");
    LOAD_CORE_SYMBOL(set_audio_sample, "retro_set_audio_sample");
    LOAD_CORE_SYMBOL(set_audio_sample_batch, "retro_set_audio_sample_batch");
    LOAD_CORE_SYMBOL(set_input_poll, "retro_set_input_poll");
    LOAD_CORE_SYMBOL(set_input_state, "retro_set_input_state");
    LOAD_CORE_SYMBOL(init, "retro_init");
    LOAD_CORE_SYMBOL(deinit, "retro_deinit");
    LOAD_CORE_SYMBOL(get_system_info, "retro_get_system_info");
    LOAD_CORE_SYMBOL(get_system_av_info, "retro_get_system_av_info");
    LOAD_CORE_SYMBOL(load_game, "retro_load_game");
    LOAD_CORE_SYMBOL(unload_game, "retro_unload_game");
    LOAD_CORE_SYMBOL(run, "retro_run");
#undef LOAD_CORE_SYMBOL

    loadSymbol(r->handle, "retro_reset", reinterpret_cast<void**>(&r->api.reset));
    loadSymbol(r->handle, "retro_serialize_size", reinterpret_cast<void**>(&r->api.serialize_size));
    loadSymbol(r->handle, "retro_serialize", reinterpret_cast<void**>(&r->api.serialize));
    loadSymbol(r->handle, "retro_unserialize", reinterpret_cast<void**>(&r->api.unserialize));
    loadSymbol(r->handle, "retro_set_controller_port_device", reinterpret_cast<void**>(&r->api.set_controller_port_device));

    if (r->api.api_version() != RETRO_API_VERSION) {
        cleanupRuntime(r); delete r; gRuntime = nullptr;
        return resultString(env, "ERRO: versão da API Libretro incompatível.");
    }

    r->api.set_environment(environmentCallback);
    r->api.set_video_refresh(videoRefreshCallback);
    r->api.set_audio_sample(audioSampleCallback);
    r->api.set_audio_sample_batch(audioBatchCallback);
    r->api.set_input_poll(inputPollCallback);
    r->api.set_input_state(inputStateCallback);
    r->api.init();
    r->initialized = true;

    retro_system_info systemInfo{};
    r->api.get_system_info(&systemInfo);

    retro_game_info gameInfo{};
    gameInfo.path = r->gamePath.c_str();
    std::vector<uint8_t> contentData;
    if (!systemInfo.need_fullpath) {
        std::ifstream input(r->gamePath, std::ios::binary | std::ios::ate);
        if (!input) {
            cleanupRuntime(r); delete r; gRuntime = nullptr;
            return resultString(env, "ERRO: não foi possível ler o arquivo do jogo.");
        }
        const auto length = input.tellg();
        if (length <= 0) {
            cleanupRuntime(r); delete r; gRuntime = nullptr;
            return resultString(env, "ERRO: arquivo do jogo vazio.");
        }
        contentData.resize(static_cast<size_t>(length));
        input.seekg(0, std::ios::beg);
        input.read(reinterpret_cast<char*>(contentData.data()), length);
        gameInfo.data = contentData.data();
        gameInfo.size = contentData.size();
    }

    if (!r->api.load_game(&gameInfo)) {
        cleanupRuntime(r); delete r; gRuntime = nullptr;
        return resultString(env, "ERRO: o núcleo recusou o arquivo. Confira formato, BIOS e compatibilidade.");
    }
    r->loaded = true;

    retro_system_av_info avInfo{};
    r->api.get_system_av_info(&avInfo);
    if (avInfo.geometry.base_width > 0) r->frameWidth = avInfo.geometry.base_width;
    if (avInfo.geometry.base_height > 0) r->frameHeight = avInfo.geometry.base_height;
    if (avInfo.timing.fps > 1.0) r->fps = avInfo.timing.fps;
    if (avInfo.timing.sample_rate > 8000.0) r->sampleRate = avInfo.timing.sample_rate;
    if (r->api.set_controller_port_device) {
        r->api.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    }

    r->running.store(true);
    r->worker = std::thread(runLoop, r);
    LOGI("Core started: %s, %.2f FPS, %.0f Hz", systemInfo.library_name ? systemInfo.library_name : "Libretro", r->fps, r->sampleRate);
    return resultString(env, std::string("OK: ") + (systemInfo.library_name ? systemInfo.library_name : "Libretro") + " inicializado.");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeSetInputMask(JNIEnv*, jobject, jint mask) {
    if (gRuntime) gRuntime->inputMask.store(static_cast<uint16_t>(mask), std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativePause(JNIEnv*, jobject, jboolean paused) {
    if (gRuntime) gRuntime->paused.store(paused == JNI_TRUE);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeGetSampleRate(JNIEnv*, jobject) {
    return gRuntime ? static_cast<jint>(gRuntime->sampleRate) : 48000;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeReadAudio(JNIEnv* env, jobject, jshortArray destination) {
    if (!gRuntime || !destination) return 0;
    const jsize capacity = env->GetArrayLength(destination);
    if (capacity <= 0) return 0;
    std::vector<jshort> output;
    size_t count = 0;
    {
        std::lock_guard<std::mutex> lock(gRuntime->audioMutex);
        count = std::min(static_cast<size_t>(capacity), gRuntime->audioCount);
        output.resize(count);
        for (size_t i = 0; i < count; ++i) {
            output[i] = gRuntime->audioRing[gRuntime->audioRead];
            gRuntime->audioRead = (gRuntime->audioRead + 1) % Runtime::AUDIO_CAPACITY;
        }
        gRuntime->audioCount -= count;
    }
    if (count > 0) env->SetShortArrayRegion(destination, 0, static_cast<jsize>(count), output.data());
    return static_cast<jint>(count);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeSaveState(JNIEnv* env, jobject) {
    Runtime* r = gRuntime;
    if (!r || !r->api.serialize_size || !r->api.serialize) return nullptr;
    std::lock_guard<std::mutex> lock(r->coreMutex);
    const size_t size = r->api.serialize_size();
    if (size == 0 || size > 64 * 1024 * 1024) return nullptr;
    std::vector<uint8_t> bytes(size);
    if (!r->api.serialize(bytes.data(), bytes.size())) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeLoadState(JNIEnv* env, jobject, jbyteArray state) {
    Runtime* r = gRuntime;
    if (!r || !state || !r->api.unserialize) return JNI_FALSE;
    const jsize size = env->GetArrayLength(state);
    if (size <= 0 || size > 64 * 1024 * 1024) return JNI_FALSE;
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    env->GetByteArrayRegion(state, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
    std::lock_guard<std::mutex> lock(r->coreMutex);
    return r->api.unserialize(bytes.data(), bytes.size()) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeReset(JNIEnv*, jobject) {
    Runtime* r = gRuntime;
    if (r && r->api.reset) {
        std::lock_guard<std::mutex> lock(r->coreMutex);
        r->api.reset();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_retroboy_arcade_emulation_NativeLibretroRuntime_nativeDestroy(JNIEnv*, jobject) {
    if (!gRuntime) return;
    Runtime* r = gRuntime;
    gRuntime = nullptr;
    cleanupRuntime(r);
    delete r;
}
