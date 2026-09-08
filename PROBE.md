# Capability probe

## Wiring

Nothing. All three are done in the current files:

- `g_vulkan` has external linkage in `driverhook.cpp`, declared in
  `driverhook.h`, which both translation units include. No loose `extern`.
- `DriverBootstrap.java` declares and calls `nativeProbe()`.
- `probe.cpp` is in the `add_library` line in `CMakeLists.txt`.

## Running it

```bash
adb push turnip_mrpurple_T28-toasted.adpkg.zip /sdcard/turnip/driver.zip
adb shell am force-stop com.gtavsource.android.bootstrap
adb logcat -c && adb shell monkey -p com.gtavsource.android.bootstrap 1
adb logcat -s probe DriverBootstrap driverhook | tee T28.txt
```

Push the package **unmodified** — the bootstrap unpacks it and reads
`libraryName` out of its `meta.json`, so `vulkan.purple.so` vs
`libvulkan_freedreno.so` sorts itself out. Swapping drivers is a push plus a
force-stop; no rebuild, no reinstall.

## What the extension list is

Extracted from `libdxvk_d3d11.so` in your APK, not from upstream DXVK. This is
what that specific build references. Win32/NV/AMD entries were dropped as
unreachable on Android.

Recorded for reference. Do **not** treat an unticked box here as fatal: a name
appearing in the binary means DXVK knows about it, not that it requires it.
Plenty are optional fast paths, fallbacks or WSI. Newer entries worth noting:

- `VK_KHR_maintenance9`, `maintenance10`, `maintenance11`
- `VK_EXT_descriptor_buffer` **and** `VK_EXT_descriptor_heap`
- `VK_KHR_shader_untyped_pointers`
- `VK_KHR_unified_image_layouts`

Those are 2025-2026 vintage, which tells you this DXVK expects a recent Turnip.
That is context for driver choice, not a pass/fail gate.

The authoritative compatibility test is DXVK itself. It names the first
requirement a device fails and stops, so the real loop is: load driver, run the
game, read what it names, address that, repeat. The probe exists to confirm the
loader path works and that `shaderInt64` is cleared — not to predict the rest.

`textureCompressionBC` is the one other bit worth watching, since GTA V's
textures are BCn throughout. Record it; don't call the project dead on it alone.

## Making the DXVK loop cheap

Since DXVK reports one failure at a time, keep the iteration cost near zero:

- `android:debuggable="true"` in the repacked manifest (the repack script
  already comments on this) enables `wrap.sh`, so you can set
  `DXVK_LOG_LEVEL=debug` and `TU_DEBUG` without rebuilding.
- Debug-level DXVK logging lists the checks it performs rather than only the
  first failure, which may hand you the whole gap list in one run.
- Driver swaps are a file push, not a rebuild.

Get those three in place before the first GTA V repack and the whole
"let DXVK tell us" strategy costs minutes per cycle instead of an evening.
