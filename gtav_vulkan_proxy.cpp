#include <android/log.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#include <cstdio>
#include <cstring>
#include <cstdarg>
#include <string>

#include <vulkan/vulkan.h>
#include <adrenotools/driver.h>

#define TAG "GTAV-Turnip"

static const char *PKG =
    "com.gtavsource.android.bootstrap";

static const char *DBG =
    "/storage/emulated/0/Games/GTAV/turnip_debug.log";

static void *g_vk = nullptr;
static PFN_vkGetInstanceProcAddr g_gipa = nullptr;

static PFN_vkEnumerateInstanceVersion
    real_vkEnumerateInstanceVersion = nullptr;

static PFN_vkEnumerateInstanceExtensionProperties
    real_vkEnumerateInstanceExtensionProperties = nullptr;

static PFN_vkCreateInstance
    real_vkCreateInstance = nullptr;

static PFN_vkEnumeratePhysicalDevices
    real_vkEnumeratePhysicalDevices = nullptr;

static PFN_vkGetPhysicalDeviceProperties
    real_vkGetPhysicalDeviceProperties = nullptr;

static PFN_vkGetPhysicalDeviceProperties2
    real_vkGetPhysicalDeviceProperties2 = nullptr;

static PFN_vkGetPhysicalDeviceFeatures2
    real_vkGetPhysicalDeviceFeatures2 = nullptr;


static void debugf(const char *fmt, ...) {
    char buf[2048];

    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "%s",
        buf
    );

    int fd = open(
        DBG,
        O_WRONLY | O_CREAT | O_APPEND,
        0666
    );

    if (fd >= 0) {
        write(fd, buf, strlen(buf));
        write(fd, "\n", 1);
        fsync(fd);
        close(fd);
    }
}


__attribute__((constructor))
static void proxy_loaded() {
    debugf("000 PROXY LOADED");
}


static std::string external_dir() {
    Dl_info info{};
    if (dladdr((void*)&external_dir, &info) == 0 || !info.dli_fname) {
        debugf("020 BUNDLED LIB DIR: dladdr failed");
        return "";
    }

    std::string path(info.dli_fname);
    debugf("020 PROXY PATH %s", path.c_str());

    const size_t slash = path.find_last_of('/');
    if (slash == std::string::npos) {
        debugf("021 BUNDLED LIB DIR: invalid proxy path");
        return "";
    }

    std::string dir = path.substr(0, slash + 1);
    debugf("021 BUNDLED LIB DIR %s", dir.c_str());
    return dir;
}


static std::string internal_dir() {
    return std::string("/data/user/0/")
        + PKG
        + "/files/turnip/";
}


static bool mkdir_ok(const std::string &p) {
    if (mkdir(p.c_str(), 0700) == 0)
        return true;

    return errno == EEXIST;
}


static bool copy_file(
    const std::string &src,
    const std::string &dst
) {
    debugf(
        "010 COPY ENTER %s -> %s",
        src.c_str(),
        dst.c_str()
    );

    int in = open(src.c_str(), O_RDONLY);

    if (in < 0) {
        debugf(
            "011 COPY SOURCE OPEN FAILED errno=%d",
            errno
        );
        return false;
    }

    int out = open(
        dst.c_str(),
        O_WRONLY | O_CREAT | O_TRUNC,
        0700
    );

    if (out < 0) {
        debugf(
            "012 COPY DEST OPEN FAILED errno=%d",
            errno
        );
        close(in);
        return false;
    }

    char buf[65536];

    for (;;) {
        ssize_t n = read(in, buf, sizeof(buf));

        if (n == 0)
            break;

        if (n < 0) {
            debugf(
                "013 COPY READ FAILED errno=%d",
                errno
            );
            close(in);
            close(out);
            return false;
        }

        char *p = buf;
        ssize_t left = n;

        while (left > 0) {
            ssize_t w = write(out, p, left);

            if (w <= 0) {
                debugf(
                    "014 COPY WRITE FAILED errno=%d",
                    errno
                );
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

    debugf("015 COPY OK");
    return true;
}


static bool initialize_turnip() {
    debugf("020 INITIALIZE ENTER");

    if (g_vk && g_gipa) {
        debugf(
            "021 ALREADY READY handle=%p gipa=%p",
            g_vk,
            reinterpret_cast<void *>(g_gipa)
        );
        return true;
    }

    const std::string ext = external_dir();
    const std::string internal = internal_dir();

    debugf("022 EXTERNAL %s", ext.c_str());
    debugf("023 INTERNAL %s", internal.c_str());

    std::string filesDir =
        std::string("/data/user/0/")
        + PKG
        + "/files";

    if (!mkdir_ok(filesDir)) {
        debugf(
            "024 FILES DIR FAILED errno=%d",
            errno
        );
        return false;
    }

    if (!mkdir_ok(internal)) {
        debugf(
            "025 TURNIP DIR FAILED errno=%d",
            errno
        );
        return false;
    }

    const char *names[] = {
        "libvulkan.purple.so",
        "libmain_hook.so",
        "libhook_impl.so"
    };

    for (const char *name : names) {
        std::string src = ext + name;
        std::string dst;

        if (strcmp(name, "libvulkan.purple.so") == 0)
            dst = internal + "vulkan.purple.so";
        else
            dst = internal + name;

        if (access(src.c_str(), R_OK) != 0) {
            debugf(
                "026 SOURCE MISSING %s errno=%d",
                src.c_str(),
                errno
            );
            return false;
        }

        debugf("027 SOURCE OK %s", name);

        if (!copy_file(src, dst)) {
            debugf(
                "028 STAGE FAILED %s",
                name
            );
            return false;
        }
    }

    std::string tmp =
        std::string("/data/user/0/")
        + PKG
        + "/cache/turnip/";

    mkdir_ok(tmp);

    debugf("030 ADRENOTOOLS ENTER");

    dlerror();

    g_vk = adrenotools_open_libvulkan(
        RTLD_NOW | RTLD_GLOBAL,
        ADRENOTOOLS_DRIVER_CUSTOM,
        tmp.c_str(),
        internal.c_str(),
        internal.c_str(),
        "vulkan.purple.so",
        nullptr,
        nullptr
    );

    debugf(
        "031 ADRENOTOOLS RETURN handle=%p",
        g_vk
    );

    if (!g_vk) {
        const char *e = dlerror();

        debugf(
            "032 ADRENOTOOLS FAILED %s",
            e ? e : "(no dlerror)"
        );

        return false;
    }

    dlerror();

    g_gipa =
        reinterpret_cast<PFN_vkGetInstanceProcAddr>(
            dlsym(
                g_vk,
                "vkGetInstanceProcAddr"
            )
        );

    const char *e = dlerror();

    debugf(
        "033 REAL GIPA=%p error=%s",
        reinterpret_cast<void *>(g_gipa),
        e ? e : "(none)"
    );

    if (!g_gipa) {
        debugf("034 GIPA FAILED");
        return false;
    }

    debugf("035 TURNIP READY");
    return true;
}


/* --------------------------------------------------------- */
/* Wrapped Vulkan calls                                      */
/* --------------------------------------------------------- */

static VkResult VKAPI_PTR
wrap_vkEnumerateInstanceVersion(
    uint32_t *pApiVersion
) {
    debugf("100 CALL vkEnumerateInstanceVersion");

    VkResult r =
        real_vkEnumerateInstanceVersion(
            pApiVersion
        );

    debugf(
        "101 RETURN vkEnumerateInstanceVersion result=%d version=%u",
        static_cast<int>(r),
        pApiVersion ? *pApiVersion : 0
    );

    return r;
}


static VkResult VKAPI_PTR
wrap_vkEnumerateInstanceExtensionProperties(
    const char *pLayerName,
    uint32_t *pPropertyCount,
    VkExtensionProperties *pProperties
) {
    debugf(
        "110 CALL vkEnumerateInstanceExtensionProperties layer=%s properties=%p count=%u",
        pLayerName ? pLayerName : "(null)",
        pProperties,
        pPropertyCount ? *pPropertyCount : 0
    );

    VkResult r =
        real_vkEnumerateInstanceExtensionProperties(
            pLayerName,
            pPropertyCount,
            pProperties
        );

    debugf(
        "111 RETURN vkEnumerateInstanceExtensionProperties result=%d count=%u",
        static_cast<int>(r),
        pPropertyCount ? *pPropertyCount : 0
    );

    return r;
}


static VkResult VKAPI_PTR
wrap_vkCreateInstance(
    const VkInstanceCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator,
    VkInstance *pInstance
) {
    uint32_t extCount =
        pCreateInfo
        ? pCreateInfo->enabledExtensionCount
        : 0;

    debugf(
        "120 CALL vkCreateInstance extensions=%u",
        extCount
    );

    if (
        pCreateInfo
        && pCreateInfo->ppEnabledExtensionNames
    ) {
        for (
            uint32_t i = 0;
            i < pCreateInfo->enabledExtensionCount;
            i++
        ) {
            debugf(
                "121 INSTANCE EXT %s",
                pCreateInfo->ppEnabledExtensionNames[i]
            );
        }
    }

    VkResult r =
        real_vkCreateInstance(
            pCreateInfo,
            pAllocator,
            pInstance
        );

    debugf(
        "122 RETURN vkCreateInstance result=%d instance=%p",
        static_cast<int>(r),
        pInstance
            ? reinterpret_cast<void *>(*pInstance)
            : nullptr
    );

    return r;
}


static VkResult VKAPI_PTR
wrap_vkEnumeratePhysicalDevices(
    VkInstance instance,
    uint32_t *pPhysicalDeviceCount,
    VkPhysicalDevice *pPhysicalDevices
) {
    debugf(
        "130 CALL vkEnumeratePhysicalDevices instance=%p devices=%p",
        reinterpret_cast<void *>(instance),
        pPhysicalDevices
    );

    VkResult r =
        real_vkEnumeratePhysicalDevices(
            instance,
            pPhysicalDeviceCount,
            pPhysicalDevices
        );

    debugf(
        "131 RETURN vkEnumeratePhysicalDevices result=%d count=%u",
        static_cast<int>(r),
        pPhysicalDeviceCount
            ? *pPhysicalDeviceCount
            : 0
    );

    return r;
}


static void VKAPI_PTR
wrap_vkGetPhysicalDeviceProperties(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties *pProperties
) {
    debugf(
        "140 CALL vkGetPhysicalDeviceProperties device=%p",
        reinterpret_cast<void *>(physicalDevice)
    );

    real_vkGetPhysicalDeviceProperties(
        physicalDevice,
        pProperties
    );

    if (pProperties) {
        debugf(
            "141 RETURN vkGetPhysicalDeviceProperties name=%s vendor=0x%04x device=0x%04x api=%u",
            pProperties->deviceName,
            pProperties->vendorID,
            pProperties->deviceID,
            pProperties->apiVersion
        );
    } else {
        debugf(
            "141 RETURN vkGetPhysicalDeviceProperties properties=NULL"
        );
    }
}


static void VKAPI_PTR
wrap_vkGetPhysicalDeviceProperties2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceProperties2 *pProperties
) {
    debugf(
        "150 CALL vkGetPhysicalDeviceProperties2 device=%p",
        reinterpret_cast<void *>(physicalDevice)
    );

    real_vkGetPhysicalDeviceProperties2(
        physicalDevice,
        pProperties
    );

    if (pProperties) {
        debugf(
            "151 RETURN vkGetPhysicalDeviceProperties2 name=%s",
            pProperties->properties.deviceName
        );
    } else {
        debugf(
            "151 RETURN vkGetPhysicalDeviceProperties2 properties=NULL"
        );
    }
}


static void VKAPI_PTR
wrap_vkGetPhysicalDeviceFeatures2(
    VkPhysicalDevice physicalDevice,
    VkPhysicalDeviceFeatures2 *pFeatures
) {
    debugf(
        "160 CALL vkGetPhysicalDeviceFeatures2 device=%p",
        reinterpret_cast<void *>(physicalDevice)
    );

    real_vkGetPhysicalDeviceFeatures2(
        physicalDevice,
        pFeatures
    );

    if (pFeatures) {
        debugf(
            "161 RETURN vkGetPhysicalDeviceFeatures2 shaderInt64=%u",
            pFeatures->features.shaderInt64
        );
    } else {
        debugf(
            "161 RETURN vkGetPhysicalDeviceFeatures2 features=NULL"
        );
    }
}


/* --------------------------------------------------------- */
/* Export seen by DXVK                                       */
/* --------------------------------------------------------- */

extern "C"
__attribute__((visibility("default")))
PFN_vkVoidFunction VKAPI_PTR
vkGetInstanceProcAddr(
    VkInstance instance,
    const char *name
) {
    debugf(
        "200 GIPA ENTER instance=%p name=%s",
        reinterpret_cast<void *>(instance),
        name ? name : "(null)"
    );

    if (!initialize_turnip()) {
        debugf("201 INITIALIZE FAILED");
        return nullptr;
    }

    PFN_vkVoidFunction fn =
        g_gipa(instance, name);

    debugf(
        "202 REAL GIPA name=%s ptr=%p",
        name ? name : "(null)",
        reinterpret_cast<void *>(fn)
    );

    if (!name || !fn)
        return fn;

    if (
        strcmp(
            name,
            "vkEnumerateInstanceVersion"
        ) == 0
    ) {
        real_vkEnumerateInstanceVersion =
            reinterpret_cast<
                PFN_vkEnumerateInstanceVersion
            >(fn);

        debugf("203 WRAP vkEnumerateInstanceVersion");

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkEnumerateInstanceVersion);
    }

    if (
        strcmp(
            name,
            "vkEnumerateInstanceExtensionProperties"
        ) == 0
    ) {
        real_vkEnumerateInstanceExtensionProperties =
            reinterpret_cast<
                PFN_vkEnumerateInstanceExtensionProperties
            >(fn);

        debugf(
            "204 WRAP vkEnumerateInstanceExtensionProperties"
        );

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkEnumerateInstanceExtensionProperties);
    }

    if (
        strcmp(
            name,
            "vkCreateInstance"
        ) == 0
    ) {
        real_vkCreateInstance =
            reinterpret_cast<
                PFN_vkCreateInstance
            >(fn);

        debugf("205 WRAP vkCreateInstance");

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkCreateInstance);
    }

    if (
        strcmp(
            name,
            "vkEnumeratePhysicalDevices"
        ) == 0
    ) {
        real_vkEnumeratePhysicalDevices =
            reinterpret_cast<
                PFN_vkEnumeratePhysicalDevices
            >(fn);

        debugf("206 WRAP vkEnumeratePhysicalDevices");

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkEnumeratePhysicalDevices);
    }

    if (
        strcmp(
            name,
            "vkGetPhysicalDeviceProperties"
        ) == 0
    ) {
        real_vkGetPhysicalDeviceProperties =
            reinterpret_cast<
                PFN_vkGetPhysicalDeviceProperties
            >(fn);

        debugf(
            "207 WRAP vkGetPhysicalDeviceProperties"
        );

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkGetPhysicalDeviceProperties);
    }

    if (
        strcmp(
            name,
            "vkGetPhysicalDeviceProperties2"
        ) == 0
    ) {
        real_vkGetPhysicalDeviceProperties2 =
            reinterpret_cast<
                PFN_vkGetPhysicalDeviceProperties2
            >(fn);

        debugf(
            "208 WRAP vkGetPhysicalDeviceProperties2"
        );

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkGetPhysicalDeviceProperties2);
    }

    if (
        strcmp(
            name,
            "vkGetPhysicalDeviceFeatures2"
        ) == 0
    ) {
        real_vkGetPhysicalDeviceFeatures2 =
            reinterpret_cast<
                PFN_vkGetPhysicalDeviceFeatures2
            >(fn);

        debugf(
            "209 WRAP vkGetPhysicalDeviceFeatures2"
        );

        return reinterpret_cast<
            PFN_vkVoidFunction
        >(wrap_vkGetPhysicalDeviceFeatures2);
    }

    debugf(
        "210 GIPA RETURN UNWRAPPED %s",
        name
    );

    return fn;
}
