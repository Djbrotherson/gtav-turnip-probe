#include <android/log.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>

#include <cstdio>
#include <cstring>
#include <string>

#include <vulkan/vulkan.h>
#include <adrenotools/driver.h>

#define TAG "GTAV-Turnip"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void *g_vk = nullptr;

static const char *PKG =
    "com.gtavsource.android.bootstrap";

static std::string ext_dir() {
    return "/storage/emulated/0/Games/GTAV/turnip/";
}

static std::string int_dir() {
    return std::string("/data/user/0/") + PKG + "/files/turnip/";
}

static bool mkdirs(const std::string &p) {
    if (mkdir(p.c_str(), 0700) == 0)
        return true;

    return errno == EEXIST;
}

static bool copy_file(
        const std::string &src,
        const std::string &dst) {

    int in = open(src.c_str(), O_RDONLY);

    if (in < 0) {
        LOGE("open source failed: %s errno=%d",
             src.c_str(), errno);
        return false;
    }

    int out = open(
        dst.c_str(),
        O_WRONLY | O_CREAT | O_TRUNC,
        0700);

    if (out < 0) {
        LOGE("open destination failed: %s errno=%d",
             dst.c_str(), errno);
        close(in);
        return false;
    }

    char buf[65536];

    for (;;) {
        ssize_t n = read(in, buf, sizeof(buf));

        if (n == 0)
            break;

        if (n < 0) {
            LOGE("read failed errno=%d", errno);
            close(in);
            close(out);
            return false;
        }

        char *p = buf;
        ssize_t left = n;

        while (left > 0) {
            ssize_t w = write(out, p, left);

            if (w <= 0) {
                LOGE("write failed errno=%d", errno);
                close(in);
                close(out);
                return false;
            }

            p += w;
            left -= w;
        }
    }

    close(in);
    close(out);

    chmod(dst.c_str(), 0700);
    return true;
}

static bool initialize_turnip() {

    if (g_vk)
        return true;

    LOGI("initializing GTA Turnip proxy");

    const std::string external = ext_dir();
    const std::string internal = int_dir();

    // Parent /files already exists for the app.
    mkdirs(
        std::string("/data/user/0/")
        + PKG
        + "/files");

    if (!mkdirs(internal)) {
        LOGE("could not create %s", internal.c_str());
        return false;
    }

    const char *driver = "vulkan.purple.so";
    const char *mainHook = "libmain_hook.so";
    const char *hookImpl = "libhook_impl.so";

    for (const char *name :
         {driver, mainHook, hookImpl}) {

        std::string src = external + name;
        std::string dst = internal + name;

        LOGI("staging %s", name);

        if (!copy_file(src, dst)) {
            LOGE("failed staging %s", name);
            return false;
        }
    }

    std::string tmp =
        std::string("/data/user/0/")
        + PKG
        + "/cache/turnip/";

    mkdirs(tmp);

    LOGI("calling adrenotools_open_libvulkan");
    LOGI("driverDir=%s", internal.c_str());

    g_vk = adrenotools_open_libvulkan(
        RTLD_NOW | RTLD_GLOBAL,
        ADRENOTOOLS_DRIVER_CUSTOM,
        tmp.c_str(),
        internal.c_str(),
        internal.c_str(),
        driver,
        nullptr,
        nullptr);

    if (!g_vk) {
        const char *e = dlerror();

        LOGE("adrenotools failed: %s",
             e ? e : "(no dlerror)");

        return false;
    }

    void *p =
        dlsym(g_vk, "vkGetInstanceProcAddr");

    if (!p) {
        LOGE("Turnip has no vkGetInstanceProcAddr");
        g_vk = nullptr;
        return false;
    }

    LOGI("TURNIP READY");
    return true;
}

extern "C"
__attribute__((visibility("default")))
PFN_vkVoidFunction
vkGetInstanceProcAddr(
        VkInstance instance,
        const char *name) {

    if (!initialize_turnip())
        return nullptr;

    auto real =
        reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(g_vk, "vkGetInstanceProcAddr"));

    if (!real)
        return nullptr;

    return real(instance, name);
}
