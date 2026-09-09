package com.gtavsource.android;

public final class DirectLoadProbe {

    static {
        System.loadLibrary("directload");
    }

    private DirectLoadProbe() {}

    public static native String test();
}
