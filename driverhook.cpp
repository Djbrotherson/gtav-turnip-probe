// driverhook.cpp — force DXVK onto a Turnip ICD inside an app that has no
// driver selector of its own.
//
// Strategy:
//   1. adrenotools_open_libvulkan() gives us a handle to the system Vulkan
//      *loader* whose ICD has been redirected to our Turnip .so.
//   2. DXVK does its own dlopen("libvulkan.so"), which would resolve to the
//      stock loader + Qualcomm ICD. So we PLT-hook dlopen inside
//      libdxvk_d3d11.so and hand back our handle instead.
//
// Why not LD_PRELOAD: libdl symbols are linker-provided on Android and are
// not reliably interposable. PLT hooking is the route the emulators use.

#include <jni.h>
#include <dlfcn.h>
#include <string.h>
#include <string>
#include <android/log.h>

#include <bytehook.h>
#include <adrenotools/driver.h>

#include "driverhook.h"

#define LOG_TAG "driverhook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// External linkage on purpose: probe.cpp references this. Keeping it in the
// anonymous namespace below would give it internal linkage and fail to link.
void *g_vulkan = nullptr;

namespace {

bool wants_vulkan(const char *name) {
    if (!name) return false;
    // DXVK asks for "libvulkan.so" and falls back to "libvulkan.so.1".
    return strstr(name, "libvulkan.so") != nullptr;
}

void *hooked_dlopen(const char *name, int flags) {
    BYTEHOOK_STACK_SCOPE();
    if (wants_vulkan(name) && g_vulkan) {
        LOGI("intercepted dlopen(\"%s\") -> turnip handle %p", name, g_vulkan);
        return g_vulkan;
    }
    return BYTEHOOK_CALL_PREV(hooked_dlopen, void *(*)(const char *, int), name, flags);
}

void *hooked_android_dlopen_ext(const char *name, int flags, const void *info) {
    BYTEHOOK_STACK_SCOPE();
    if (wants_vulkan(name) && g_vulkan) {
        LOGI("intercepted android_dlopen_ext(\"%s\")", name);
        return g_vulkan;
    }
    return BYTEHOOK_CALL_PREV(hooked_android_dlopen_ext,
                              void *(*)(const char *, int, const void *),
                              name, flags, info);
}

// DXVK closes the loader on teardown. Swallow that so a soft restart of the
// renderer doesn't unload Turnip out from under us.
int hooked_dlclose(void *handle) {
    BYTEHOOK_STACK_SCOPE();
    if (handle == g_vulkan) {
        LOGI("swallowed dlclose on turnip handle");
        return 0;
    }
    return BYTEHOOK_CALL_PREV(hooked_dlclose, int (*)(void *), handle);
}

void install_hooks() {
    bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);

    // Hook only the DXVK modules; leaving the rest of the process alone keeps
    // the SDL/EGL path on the stock driver, which is what you want for the
    // window surface.
    for (const char *caller : {"libdxvk_d3d11.so", "libdxvk_dxgi.so"}) {
        bytehook_hook_single(caller, nullptr, "dlopen",
                             (void *)hooked_dlopen, nullptr, nullptr);
        bytehook_hook_single(caller, nullptr, "android_dlopen_ext",
                             (void *)hooked_android_dlopen_ext, nullptr, nullptr);
        bytehook_hook_single(caller, nullptr, "dlclose",
                             (void *)hooked_dlclose, nullptr, nullptr);
    }
    LOGI("PLT hooks installed");
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gtavsource_android_DriverBootstrap_nativeInit(
        JNIEnv *env, jclass,
        jstring jHookLibDir,   // app nativeLibraryDir — holds libmain_hook.so etc.
        jstring jDriverDir,    // dir containing the Turnip .so, TRAILING SLASH
        jstring jDriverName,   // must match "libraryName" in the driver's meta.json
        jstring jTmpDir) {     // app cacheDir, adrenotools writes a patched copy here

    auto get = [&](jstring s) {
        const char *c = env->GetStringUTFChars(s, nullptr);
        std::string out(c);
        env->ReleaseStringUTFChars(s, c);
        return out;
    };

    std::string hookLibDir = get(jHookLibDir);
    std::string driverDir  = get(jDriverDir);
    std::string driverName = get(jDriverName);
    std::string tmpDir     = get(jTmpDir);

    if (!hookLibDir.empty() && hookLibDir.back() != '/') hookLibDir += '/';
    if (!driverDir.empty()  && driverDir.back()  != '/') driverDir  += '/';
    if (!tmpDir.empty()     && tmpDir.back()     != '/') tmpDir     += '/';

    LOGI("loading %s from %s", driverName.c_str(), driverDir.c_str());

    g_vulkan = adrenotools_open_libvulkan(
            RTLD_NOW | RTLD_GLOBAL,
            ADRENOTOOLS_DRIVER_CUSTOM,
            tmpDir.c_str(),
            hookLibDir.c_str(),
            driverDir.c_str(),
            driverName.c_str(),
            nullptr,   // no file redirect
            nullptr);  // no GPU mapping import

    if (!g_vulkan) {
        LOGE("adrenotools_open_libvulkan failed: %s", dlerror());
        return JNI_FALSE;
    }

    // Sanity check before we commit to redirecting DXVK at it.
    if (!dlsym(g_vulkan, "vkGetInstanceProcAddr")) {
        LOGE("loaded handle has no vkGetInstanceProcAddr — wrong driver file?");
        g_vulkan = nullptr;
        return JNI_FALSE;
    }

    install_hooks();
    return JNI_TRUE;
}
