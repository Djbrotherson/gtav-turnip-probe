# GTAV Turnip capability probe — native CI build

This project builds the four arm64-v8a native libraries needed by the GTAV Turnip capability probe:

- libdriverhook.so
- libmain_hook.so
- libhook_impl.so
- libbytehook.so

The workflow deliberately pins ByteHook to v1.0.10. Current ByteHook releases add a ShadowHook dependency; the probe source only needs ByteHook's PLT hooking API and v1.0.10 keeps the native build self-contained.

## Build
Push this folder to GitHub, open Actions, run **Build GTAV Turnip Probe Native Libs**, then download the `gtav-turnip-probe-arm64` artifact.

The GTA APK is intentionally not stored in this repository. Repacking is done locally in Termux after the native artifact is downloaded.
