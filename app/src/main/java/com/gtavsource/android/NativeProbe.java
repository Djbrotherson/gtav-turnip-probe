package com.gtavsource.android;

public final class NativeProbe {

    private static boolean loaded = false;

    private NativeProbe() {}

    public static void loadLibrary() {
        if (!loaded) {
            System.loadLibrary("probeonly");
            loaded = true;
        }
    }

    public static native boolean nativeInit(
            String hookLibDir,
            String driverDir,
            String driverName,
            String tmpDir);

    public static native String nativeProbe();
}
