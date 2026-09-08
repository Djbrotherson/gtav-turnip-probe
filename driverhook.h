#pragma once

// Handle to the Vulkan loader that adrenotools opened against the custom ICD.
// Set by nativeInit in driverhook.cpp; read by the PLT hooks there and by the
// capability probe. Declared here rather than as a loose `extern` in probe.cpp
// so the definition and the declaration cannot drift out of sync.
extern void *g_vulkan;
