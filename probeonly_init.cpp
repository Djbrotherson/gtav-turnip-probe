#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <string>

#include "driverhook.h"
#include <adrenotools/driver.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "probeonly", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "probeonly", __VA_ARGS__)

// Shared with probeonly_probe.cpp
void *g_vulkan = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gtavsource_android_NativeProbe_nativeInit(
        JNIEnv *env, jclass,
        jstring jHookLibDir,
        jstring jDriverDir,
        jstring jDriverName,
        jstring jTmpDir) {

    auto get = [&](jstring s) {
        const char *c = env->GetStringUTFChars(s, nullptr);
        std::string out(c ? c : "");
        if (c) env->ReleaseStringUTFChars(s, c);
        return out;
    };

    std::string hookLibDir = get(jHookLibDir);
    std::string driverDir  = get(jDriverDir);
    std::string driverName = get(jDriverName);
    std::string tmpDir     = get(jTmpDir);

    if (!hookLibDir.empty() && hookLibDir.back() != '/') hookLibDir += '/';
    if (!driverDir.empty()  && driverDir.back()  != '/') driverDir  += '/';
    if (!tmpDir.empty()     && tmpDir.back()     != '/') tmpDir     += '/';

    LOGI("AdrenoTools-only load: %s from %s",
         driverName.c_str(), driverDir.c_str());

    // IMPORTANT:
    // No ByteHook.
    // No DXVK hooks.
    // Just ask AdrenoTools to load the custom Vulkan ICD.
    g_vulkan = adrenotools_open_libvulkan(
            RTLD_NOW | RTLD_GLOBAL,
            ADRENOTOOLS_DRIVER_CUSTOM,
            tmpDir.c_str(),
            hookLibDir.c_str(),
            driverDir.c_str(),
            driverName.c_str(),
            nullptr,
            nullptr);

    if (!g_vulkan) {
        LOGE("adrenotools_open_libvulkan returned null: %s", dlerror());
        return JNI_FALSE;
    }

    if (!dlsym(g_vulkan, "vkGetInstanceProcAddr")) {
        LOGE("Turnip handle has no vkGetInstanceProcAddr");
        g_vulkan = nullptr;
        return JNI_FALSE;
    }

    LOGI("Turnip Vulkan handle is valid");
    return JNI_TRUE;
}
