// probe.cpp — bolt-on for driverhook.cpp.
//
// Reports every feature and extension name referenced by the DXVK build that
// ships in GTAV_Android.apk, queried through whichever ICD driverhook loaded.
// One pass, complete answer — rather than fixing shaderInt64 and discovering
// descriptorBuffer is also missing on the next rebuild.
//
// Call Java_com_gtavsource_android_NativeProbe_nativeProbe AFTER
// nativeInit has returned true.

#include <jni.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <sstream>
#include <set>

#include "driverhook.h"   // brings in g_vulkan

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "probe", __VA_ARGS__)

namespace {

std::ostringstream out;

void line(const std::string &s) { out << s << '\n'; LOGI("%s", s.c_str()); }

void feat(const char *name, VkBool32 v, bool *allOk = nullptr) {
    out << (v ? "  [x] " : "  [ ] ") << name << '\n';
    if (!v && allOk) *allOk = false;
    if (!v) LOGI("MISSING FEATURE %s", name);
}

// Extensions this DXVK build references, extracted from libdxvk_d3d11.so.
// Win32/NV/AMD-specific ones are excluded — they are not reachable on Android.
const char *kExtensions[] = {
    "VK_EXT_attachment_feedback_loop_layout",
    "VK_EXT_border_color_swizzle",
    "VK_EXT_conservative_rasterization",
    "VK_EXT_custom_border_color",
    "VK_EXT_depth_bias_control",
    "VK_EXT_depth_clip_enable",
    "VK_EXT_descriptor_buffer",
    "VK_EXT_descriptor_heap",
    "VK_EXT_dynamic_rendering_unused_attachments",
    "VK_EXT_extended_dynamic_state3",
    "VK_EXT_fragment_shader_interlock",
    "VK_EXT_graphics_pipeline_library",
    "VK_EXT_hdr_metadata",
    "VK_EXT_line_rasterization",
    "VK_EXT_memory_budget",
    "VK_EXT_memory_priority",
    "VK_EXT_multi_draw",
    "VK_EXT_non_seamless_cube_map",
    "VK_EXT_pageable_device_local_memory",
    "VK_EXT_robustness2",
    "VK_EXT_sample_locations",
    "VK_EXT_shader_module_identifier",
    "VK_EXT_shader_stencil_export",
    "VK_EXT_swapchain_maintenance1",
    "VK_EXT_transform_feedback",
    "VK_EXT_vertex_attribute_divisor",
    "VK_KHR_device_fault",
    "VK_KHR_dynamic_rendering_local_read",
    "VK_KHR_load_store_op_none",
    "VK_KHR_maintenance5",
    "VK_KHR_maintenance6",
    "VK_KHR_maintenance7",
    "VK_KHR_maintenance8",
    "VK_KHR_maintenance9",
    "VK_KHR_maintenance10",
    "VK_KHR_maintenance11",
    "VK_KHR_pipeline_library",
    "VK_KHR_present_id",
    "VK_KHR_present_wait",
    "VK_KHR_shader_float_controls2",
    "VK_KHR_shader_subgroup_uniform_control_flow",
    "VK_KHR_shader_untyped_pointers",
    "VK_KHR_swapchain",
    "VK_KHR_swapchain_mutable_format",
    "VK_KHR_unified_image_layouts",
};

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_gtavsource_android_NativeProbe_nativeProbe(JNIEnv *env, jclass) {
    out.str("");

    if (!g_vulkan) return env->NewStringUTF("driver not loaded — run nativeInit first");

    auto gipa = (PFN_vkGetInstanceProcAddr)dlsym(g_vulkan, "vkGetInstanceProcAddr");
    if (!gipa) return env->NewStringUTF("no vkGetInstanceProcAddr on handle");

#define IPA(name) auto name = (PFN_##name)gipa(nullptr, #name)
    IPA(vkCreateInstance);
#undef IPA

    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "turnip-probe";
    app.apiVersion = VK_API_VERSION_1_3;

    VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ici.pApplicationInfo = &app;

    VkInstance inst = VK_NULL_HANDLE;
    if (vkCreateInstance(&ici, nullptr, &inst) != VK_SUCCESS)
        return env->NewStringUTF("vkCreateInstance failed — ICD is not Vulkan 1.3 capable");

    auto vkEnumeratePhysicalDevices =
        (PFN_vkEnumeratePhysicalDevices)gipa(inst, "vkEnumeratePhysicalDevices");
    auto vkGetPhysicalDeviceProperties2 =
        (PFN_vkGetPhysicalDeviceProperties2)gipa(inst, "vkGetPhysicalDeviceProperties2");
    auto vkGetPhysicalDeviceFeatures2 =
        (PFN_vkGetPhysicalDeviceFeatures2)gipa(inst, "vkGetPhysicalDeviceFeatures2");
    auto vkEnumerateDeviceExtensionProperties =
        (PFN_vkEnumerateDeviceExtensionProperties)gipa(inst, "vkEnumerateDeviceExtensionProperties");

    uint32_t n = 0;
    vkEnumeratePhysicalDevices(inst, &n, nullptr);
    std::vector<VkPhysicalDevice> devs(n);
    vkEnumeratePhysicalDevices(inst, &n, devs.data());

    if (!n) return env->NewStringUTF("no physical devices");

    for (auto dev : devs) {
        VkPhysicalDeviceDriverProperties drv{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES};
        VkPhysicalDeviceProperties2 props{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2, &drv};
        vkGetPhysicalDeviceProperties2(dev, &props);

        line("================================================");
        line(std::string("Device : ") + props.properties.deviceName);
        line(std::string("Driver : ") + drv.driverName + " " + drv.driverInfo);
        {
            uint32_t v = props.properties.apiVersion;
            line("Vulkan : " + std::to_string(VK_API_VERSION_MAJOR(v)) + "." +
                 std::to_string(VK_API_VERSION_MINOR(v)) + "." +
                 std::to_string(VK_API_VERSION_PATCH(v)));
        }
        line("================================================");

        // ---- feature chain -------------------------------------------------
        VkPhysicalDeviceVulkan11Features v11{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES};
        VkPhysicalDeviceVulkan12Features v12{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
        VkPhysicalDeviceVulkan13Features v13{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES};
        VkPhysicalDeviceRobustness2FeaturesEXT rb2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ROBUSTNESS_2_FEATURES_EXT};
        VkPhysicalDeviceTransformFeedbackFeaturesEXT xfb{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TRANSFORM_FEEDBACK_FEATURES_EXT};
        VkPhysicalDeviceDepthClipEnableFeaturesEXT dce{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DEPTH_CLIP_ENABLE_FEATURES_EXT};
        VkPhysicalDeviceCustomBorderColorFeaturesEXT cbc{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_CUSTOM_BORDER_COLOR_FEATURES_EXT};
        VkPhysicalDeviceExtendedDynamicState3FeaturesEXT eds3{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTENDED_DYNAMIC_STATE_3_FEATURES_EXT};
        VkPhysicalDeviceGraphicsPipelineLibraryFeaturesEXT gpl{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_GRAPHICS_PIPELINE_LIBRARY_FEATURES_EXT};
        VkPhysicalDeviceNonSeamlessCubeMapFeaturesEXT nscm{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_NON_SEAMLESS_CUBE_MAP_FEATURES_EXT};
        VkPhysicalDeviceMultiDrawFeaturesEXT mdraw{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT};
        VkPhysicalDeviceDescriptorBufferFeaturesEXT dbuf{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_BUFFER_FEATURES_EXT};

        void **tail = nullptr;
        VkPhysicalDeviceFeatures2 f2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
        auto chain = [&](void *p, void **pNext) {
            if (tail) *tail = p; else f2.pNext = p;
            tail = pNext;
        };
        chain(&v11, (void **)&v11.pNext);
        chain(&v12, (void **)&v12.pNext);
        chain(&v13, (void **)&v13.pNext);
        chain(&rb2, (void **)&rb2.pNext);
        chain(&xfb, (void **)&xfb.pNext);
        chain(&dce, (void **)&dce.pNext);
        chain(&cbc, (void **)&cbc.pNext);
        chain(&eds3, (void **)&eds3.pNext);
        chain(&gpl, (void **)&gpl.pNext);
        chain(&nscm, (void **)&nscm.pNext);
        chain(&mdraw, (void **)&mdraw.pNext);
        chain(&dbuf, (void **)&dbuf.pNext);

        vkGetPhysicalDeviceFeatures2(dev, &f2);
        const auto &c = f2.features;

        bool blocker = true;

        line("");
        line("-- the one DXVK rejected you on ----------------");
        feat("shaderInt64", c.shaderInt64, &blocker);

        line("");
        line("-- core 1.0 ------------------------------------");
        feat("fullDrawIndexUint32", c.fullDrawIndexUint32);
        feat("imageCubeArray", c.imageCubeArray);
        feat("independentBlend", c.independentBlend);
        feat("geometryShader", c.geometryShader);
        feat("tessellationShader", c.tessellationShader);
        feat("sampleRateShading", c.sampleRateShading);
        feat("dualSrcBlend", c.dualSrcBlend);
        feat("logicOp", c.logicOp);
        feat("multiDrawIndirect", c.multiDrawIndirect);
        feat("drawIndirectFirstInstance", c.drawIndirectFirstInstance);
        feat("depthClamp", c.depthClamp);
        feat("depthBiasClamp", c.depthBiasClamp);
        feat("fillModeNonSolid", c.fillModeNonSolid);
        feat("depthBounds", c.depthBounds);
        feat("wideLines", c.wideLines);
        feat("largePoints", c.largePoints);
        feat("multiViewport", c.multiViewport);
        feat("samplerAnisotropy", c.samplerAnisotropy);
        feat("textureCompressionBC", c.textureCompressionBC);   // GTA V ships BCn
        feat("occlusionQueryPrecise", c.occlusionQueryPrecise);
        feat("pipelineStatisticsQuery", c.pipelineStatisticsQuery);
        feat("vertexPipelineStoresAndAtomics", c.vertexPipelineStoresAndAtomics);
        feat("fragmentStoresAndAtomics", c.fragmentStoresAndAtomics);
        feat("shaderImageGatherExtended", c.shaderImageGatherExtended);
        feat("shaderStorageImageExtendedFormats", c.shaderStorageImageExtendedFormats);
        feat("shaderStorageImageReadWithoutFormat", c.shaderStorageImageReadWithoutFormat);
        feat("shaderStorageImageWriteWithoutFormat", c.shaderStorageImageWriteWithoutFormat);
        feat("shaderClipDistance", c.shaderClipDistance);
        feat("shaderCullDistance", c.shaderCullDistance);
        feat("shaderFloat64", c.shaderFloat64);
        feat("shaderResourceMinLod", c.shaderResourceMinLod);
        feat("variableMultisampleRate", c.variableMultisampleRate);

        line("");
        line("-- 1.1 / 1.2 / 1.3 -----------------------------");
        feat("shaderDrawParameters", v11.shaderDrawParameters);
        feat("storageBuffer16BitAccess", v11.storageBuffer16BitAccess);
        feat("descriptorIndexing", v12.descriptorIndexing);
        feat("runtimeDescriptorArray", v12.runtimeDescriptorArray);
        feat("descriptorBindingPartiallyBound", v12.descriptorBindingPartiallyBound);
        feat("descriptorBindingSampledImageUpdateAfterBind", v12.descriptorBindingSampledImageUpdateAfterBind);
        feat("samplerFilterMinmax", v12.samplerFilterMinmax);
        feat("scalarBlockLayout", v12.scalarBlockLayout);
        feat("uniformBufferStandardLayout", v12.uniformBufferStandardLayout);
        feat("hostQueryReset", v12.hostQueryReset);
        feat("timelineSemaphore", v12.timelineSemaphore);
        feat("bufferDeviceAddress", v12.bufferDeviceAddress);
        feat("vulkanMemoryModel", v12.vulkanMemoryModel);
        feat("shaderOutputViewportIndex", v12.shaderOutputViewportIndex);
        feat("shaderOutputLayer", v12.shaderOutputLayer);
        feat("shaderFloat16", v12.shaderFloat16);
        feat("shaderInt8", v12.shaderInt8);
        feat("drawIndirectCount", v12.drawIndirectCount);
        feat("samplerMirrorClampToEdge", v12.samplerMirrorClampToEdge);
        feat("dynamicRendering", v13.dynamicRendering);
        feat("synchronization2", v13.synchronization2);
        feat("maintenance4", v13.maintenance4);
        feat("shaderDemoteToHelperInvocation", v13.shaderDemoteToHelperInvocation);
        feat("shaderZeroInitializeWorkgroupMemory", v13.shaderZeroInitializeWorkgroupMemory);
        feat("subgroupSizeControl", v13.subgroupSizeControl);
        feat("computeFullSubgroups", v13.computeFullSubgroups);
        feat("inlineUniformBlock", v13.inlineUniformBlock);
        feat("pipelineCreationCacheControl", v13.pipelineCreationCacheControl);
        feat("robustImageAccess", v13.robustImageAccess);

        line("");
        line("-- extension features --------------------------");
        feat("robustBufferAccess2", rb2.robustBufferAccess2);
        feat("robustImageAccess2", rb2.robustImageAccess2);
        feat("nullDescriptor", rb2.nullDescriptor);
        feat("transformFeedback", xfb.transformFeedback);
        feat("geometryStreams", xfb.geometryStreams);
        feat("depthClipEnable", dce.depthClipEnable);
        feat("customBorderColors", cbc.customBorderColors);
        feat("customBorderColorWithoutFormat", cbc.customBorderColorWithoutFormat);
        feat("graphicsPipelineLibrary", gpl.graphicsPipelineLibrary);
        feat("nonSeamlessCubeMap", nscm.nonSeamlessCubeMap);
        feat("multiDraw", mdraw.multiDraw);
        feat("descriptorBuffer", dbuf.descriptorBuffer);
        feat("extendedDynamicState3AlphaToCoverageEnable", eds3.extendedDynamicState3AlphaToCoverageEnable);
        feat("extendedDynamicState3DepthClipEnable", eds3.extendedDynamicState3DepthClipEnable);
        feat("extendedDynamicState3RasterizationSamples", eds3.extendedDynamicState3RasterizationSamples);
        feat("extendedDynamicState3SampleMask", eds3.extendedDynamicState3SampleMask);
        feat("extendedDynamicState3LineRasterizationMode", eds3.extendedDynamicState3LineRasterizationMode);

        // ---- extensions ----------------------------------------------------
        uint32_t en = 0;
        vkEnumerateDeviceExtensionProperties(dev, nullptr, &en, nullptr);
        std::vector<VkExtensionProperties> exts(en);
        vkEnumerateDeviceExtensionProperties(dev, nullptr, &en, exts.data());
        std::set<std::string> have;
        for (auto &e : exts) have.insert(e.extensionName);

        line("");
        line("-- extensions DXVK references ------------------");
        for (const char *x : kExtensions) {
            bool ok = have.count(x) > 0;
            out << (ok ? "  [x] " : "  [ ] ") << x << '\n';
            if (!ok) LOGI("MISSING EXT %s", x);
        }

        line("");
        line(blocker
             ? ">>> shaderInt64 PRESENT. This clears the ONE feature DXVK named."
             : ">>> shaderInt64 ABSENT. This driver cannot work; try the next one.");
        line("");
        line("This is NOT a compatibility verdict. The list above is what this");
        line("DXVK build references, which is not the same as what it requires --");
        line("many entries are optional paths, fallbacks or WSI. An unticked box");
        line("is a data point to record, not a reason to stop.");
        line("The authoritative test is DXVK itself: wire this driver into the");
        line("game and read what it names next, if anything.");
    }

    return env->NewStringUTF(out.str().c_str());
}
