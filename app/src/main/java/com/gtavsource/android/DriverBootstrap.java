package com.gtavsource.android;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DriverBootstrap {

    static {
        System.loadLibrary("driverhook");
    }

    private static File stagedDir;
    private static String stagedLibrary;
    private static String stagedName;
    private static String stagedVersion;

    private DriverBootstrap() {}

    public static String stageDriver(Context context, Uri driverPackage) {
        try {
            File driverDir = new File(context.getFilesDir(), "driver");
            deleteDir(driverDir);

            if (!driverDir.mkdirs() && !driverDir.isDirectory()) {
                return "STAGE FAILED\nCould not create driver directory.";
            }

            try (InputStream in =
                         context.getContentResolver().openInputStream(driverPackage)) {
                if (in == null) {
                    return "STAGE FAILED\nCould not open selected ZIP.";
                }
                unzip(in, driverDir);
            }

            File meta = new File(driverDir, "meta.json");

            if (!meta.exists()) {
                return "STAGE FAILED\nNo meta.json found in selected ZIP.";
            }

            JSONObject j = new JSONObject(readAll(meta));

            String libraryName = j.getString("libraryName");
            String name = j.optString("name", "?");
            String version = j.optString("driverVersion", "?");

            File driver = new File(driverDir, libraryName);

            if (!driver.exists()) {
                return "STAGE FAILED\n\n"
                        + "meta.json says:\n"
                        + libraryName
                        + "\n\nbut that library is not present.";
            }

            stagedDir = driverDir;
            stagedLibrary = libraryName;
            stagedName = name;
            stagedVersion = version;

            return "STAGE 1 PASSED\n\n"
                    + "Driver: " + name + "\n"
                    + "Version: " + version + "\n"
                    + "Library: " + libraryName + "\n"
                    + "Size: " + driver.length() + " bytes\n\n"
                    + "ZIP extraction and meta.json are good.\n\n"
                    + "Press LOAD TURNIP + RUN PROBE.";

        } catch (Throwable t) {
            return throwable("STAGE FAILED", t);
        }
    }

    public static String runProbe(Context context) {
        if (stagedDir == null || stagedLibrary == null) {
            return "NO DRIVER STAGED";
        }

        try {
            boolean ok = nativeInit(
                    context.getApplicationInfo().nativeLibraryDir,
                    stagedDir.getAbsolutePath() + "/",
                    stagedLibrary,
                    context.getCacheDir().getAbsolutePath()
            );

            if (!ok) {
                return "NATIVE LOAD RETURNED FALSE\n\n"
                        + "Driver: " + stagedName + "\n"
                        + "Version: " + stagedVersion + "\n"
                        + "Library: " + stagedLibrary;
            }

            String report = nativeProbe();

            return "TURNIP LOAD PASSED\n\n"
                    + "Driver: " + stagedName + "\n"
                    + "Version: " + stagedVersion + "\n"
                    + "Library: " + stagedLibrary + "\n\n"
                    + report;

        } catch (Throwable t) {
            return throwable("NATIVE PROBE FAILED", t);
        }
    }

    private static String throwable(String title, Throwable t) {
        StringBuilder s = new StringBuilder();
        s.append(title).append("\n\n");
        s.append(t.toString());

        for (StackTraceElement e : t.getStackTrace()) {
            s.append("\n  at ").append(e);
        }

        return s.toString();
    }

    private static void unzip(InputStream raw, File dest) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(raw)) {
            ZipEntry e;

            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;

                String name = new File(e.getName()).getName();
                if (name.isEmpty()) continue;

                File outFile = new File(dest, name);

                try (OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[65536];
                    int n;

                    while ((n = zin.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static String readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;

            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }

            return out.toString("UTF-8");
        }
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;

        File[] kids = f.listFiles();

        if (kids != null) {
            for (File k : kids) deleteDir(k);
        }

        f.delete();
    }

    private static native boolean nativeInit(
            String hookLibDir,
            String driverDir,
            String driverName,
            String tmpDir
    );

    private static native String nativeProbe();
}
