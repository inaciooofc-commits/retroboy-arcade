#include <jni.h>
#include <dlfcn.h>
#include <cstdio>
#include <string>
#include <vector>

using RetroApiVersion = unsigned (*)();

extern "C"
JNIEXPORT jstring JNICALL
Java_com_retroboy_arcade_emulation_NativeCoreProbe_probeCore(
    JNIEnv* env,
    jobject /* instance */,
    jstring library_path
) {
    if (library_path == nullptr) {
        return env->NewStringUTF("ERROR: caminho da biblioteca ausente");
    }

    const char* path = env->GetStringUTFChars(library_path, nullptr);
    if (path == nullptr) {
        return env->NewStringUTF("ERROR: não foi possível ler o caminho");
    }

    void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        const char* reason = dlerror();
        std::string message = "ERROR: dlopen: ";
        message += reason ? reason : "falha desconhecida";
        env->ReleaseStringUTFChars(library_path, path);
        return env->NewStringUTF(message.c_str());
    }

    const char* required[] = {
        "retro_api_version",
        "retro_set_environment",
        "retro_set_video_refresh",
        "retro_set_audio_sample",
        "retro_set_audio_sample_batch",
        "retro_set_input_poll",
        "retro_set_input_state",
        "retro_init",
        "retro_deinit",
        "retro_get_system_info",
        "retro_load_game",
        "retro_unload_game",
        "retro_run"
    };

    std::vector<std::string> missing;
    for (const char* symbol : required) {
        dlerror();
        if (dlsym(handle, symbol) == nullptr) {
            missing.emplace_back(symbol);
        }
    }

    unsigned api_version = 0;
    auto api_fn = reinterpret_cast<RetroApiVersion>(dlsym(handle, "retro_api_version"));
    if (api_fn != nullptr) {
        api_version = api_fn();
    }

    dlclose(handle);
    env->ReleaseStringUTFChars(library_path, path);

    if (!missing.empty()) {
        std::string message = "ERROR: exports ausentes: ";
        for (size_t i = 0; i < missing.size(); ++i) {
            if (i) message += ", ";
            message += missing[i];
        }
        return env->NewStringUTF(message.c_str());
    }

    char result[128];
    std::snprintf(result, sizeof(result), "OK: Libretro API %u; %zu exports verificados", api_version, sizeof(required) / sizeof(required[0]));
    return env->NewStringUTF(result);
}
