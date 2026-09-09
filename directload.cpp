#include <jni.h>
#include <dlfcn.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_gtavsource_android_DirectLoadProbe_test(
        JNIEnv *env,
        jclass) {

    std::string out;

    dlerror();

    void *h = dlopen("libturnip.so", RTLD_NOW | RTLD_LOCAL);

    if (!h) {
        const char *e = dlerror();

        out =
            "DIRECT LOAD FAILED\n\n"
            "dlopen(\"libturnip.so\") returned NULL.\n\n"
            "dlerror:\n";

        out += e ? e : "(none)";

        return env->NewStringUTF(out.c_str());
    }

    out =
        "DIRECT DLOPEN PASSED\n\n"
        "libturnip.so loaded successfully.\n\n";

    dlerror();

    void *gipa = dlsym(h, "vkGetInstanceProcAddr");
    const char *e = dlerror();

    if (!gipa) {
        out +=
            "VK ENTRYPOINT FAILED\n\n"
            "vkGetInstanceProcAddr was not found.\n\n"
            "dlerror:\n";

        out += e ? e : "(none)";
    } else {
        out +=
            "VK ENTRYPOINT PASSED\n\n"
            "vkGetInstanceProcAddr found.\n\n"
            "This means DXVK can potentially load T28 "
            "directly without ByteHook or AdrenoTools.";
    }

    return env->NewStringUTF(out.c_str());
}
