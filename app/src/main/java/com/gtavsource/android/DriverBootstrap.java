package com.gtavsource.android;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DriverBootstrap {

    private static File stagedDir;
    private static String stagedLibrary;
    private static String stagedName;
    private static String stagedVersion;
    private static boolean turnipLoaded = false;

    private DriverBootstrap() {}

    public static String verifyAsset(Context context) {
        try {
            long total = 0;
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            try (InputStream in = context.getAssets().open("driver.zip")) {
                byte[] buf = new byte[65536];
                int n;

                while ((n = in.read(buf)) > 0) {
                    total += n;
                    md.update(buf, 0, n);
                }
            }

            return "STAGE 1 PASSED\n\n"
                    + "Bundled T28 driver.zip opened successfully.\n"
                    + "Bytes: " + total + "\n"
                    + "SHA256: " + hex(md.digest()) + "\n\n"
                    + "No native code has been loaded.\n\n"
                    + "Press 2. EXTRACT + VALIDATE T28.";

        } catch (Throwable t) {
            return throwable("STAGE 1 FAILED", t);
        }
    }

    public static String extractDriver(Context context) {
        try {
            File dir = new File(context.getFilesDir(), "driver");
            deleteDir(dir);

            if (!dir.mkdirs() && !dir.isDirectory()) {
                return "STAGE 2 FAILED\nCould not create private driver directory.";
            }

            try (InputStream raw = context.getAssets().open("driver.zip");
                 ZipInputStream zin = new ZipInputStream(raw)) {

                ZipEntry e;

                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;

                    String name = new File(e.getName()).getName();
                    if (name.isEmpty()) continue;

                    File outFile = new File(dir, name);

                    try (OutputStream out = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[65536];
                        int n;

                        while ((n = zin.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            }

            File meta = new File(dir, "meta.json");

            if (!meta.exists()) {
                return "STAGE 2 FAILED\nmeta.json was not extracted.";
            }

            JSONObject j = new JSONObject(readAll(meta));

            stagedLibrary = j.getString("libraryName");
            stagedName = j.optString("name", "?");
            stagedVersion = j.optString("driverVersion", "?");
            stagedDir = dir;

            File lib = new File(dir, stagedLibrary);

            if (!lib.exists()) {
                return "STAGE 2 FAILED\n\n"
                        + "meta.json requests:\n"
                        + stagedLibrary
                        + "\n\nbut that library was not extracted.";
            }

            return "STAGE 2 PASSED\n\n"
                    + "Driver: " + stagedName + "\n"
                    + "Version: " + stagedVersion + "\n"
                    + "Library: " + stagedLibrary + "\n"
                    + "Library bytes: " + lib.length() + "\n\n"
                    + "Expected T28 library: vulkan.purple.so\n\n"
                    + "Still no native code loaded.\n\n"
                    + "Press 3. LOAD TURNIP.";

        } catch (Throwable t) {
            return throwable("STAGE 2 FAILED", t);
        }
    }

    public static String loadTurnip(Context context) {
        if (stagedDir == null || stagedLibrary == null) {
            return "STAGE 3 FAILED\nRun Stage 2 first.";
        }

        try {
            System.loadLibrary("driverhook");

            boolean ok = nativeInit(
                    context.getApplicationInfo().nativeLibraryDir,
                    stagedDir.getAbsolutePath() + "/",
                    stagedLibrary,
                    context.getCacheDir().getAbsolutePath()
            );

            if (!ok) {
                return "STAGE 3 FAILED\n\n"
                        + "nativeInit returned false.\n\n"
                        + "Driver: " + stagedName + "\n"
                        + "Version: " + stagedVersion + "\n"
                        + "Library: " + stagedLibrary;
            }

            turnipLoaded = true;

            return "STAGE 3 PASSED\n\n"
                    + "CUSTOM TURNIP LOADER INITIALIZED\n\n"
                    + "Driver: " + stagedName + "\n"
                    + "Version: " + stagedVersion + "\n"
                    + "Library: " + stagedLibrary + "\n\n"
                    + "Press 4. RUN VULKAN CAPABILITY PROBE.";

        } catch (Throwable t) {
            return throwable("STAGE 3 FAILED", t);
        }
    }

    public static String runCapabilityProbe() {
        if (!turnipLoaded) {
            return "STAGE 4 FAILED\nTurnip has not been loaded.";
        }

        try {
            return "STAGE 4 RESULT\n\n" + nativeProbe();
        } catch (Throwable t) {
            return throwable("STAGE 4 FAILED", t);
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

    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder();

        for (byte b : data) {
            s.append(String.format("%02x", b));
        }

        return s.toString();
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

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;

        File[] children = f.listFiles();

        if (children != null) {
            for (File c : children) {
                deleteDir(c);
            }
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
