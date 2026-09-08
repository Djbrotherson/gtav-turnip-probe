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
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DriverBootstrap {

    private static File copiedZip;
    private static File stagedDir;
    private static String stagedLibrary;
    private static String stagedName;
    private static String stagedVersion;

    private DriverBootstrap() {}

    public static String copyZip(Context context, Uri uri) {
        try {
            File dir = new File(context.getFilesDir(), "incoming");
            if (!dir.exists() && !dir.mkdirs()) {
                return "COPY FAILED\nCould not create incoming directory.";
            }

            copiedZip = new File(dir, "driver.zip");

            try (InputStream in =
                         context.getContentResolver().openInputStream(uri);
                 OutputStream out =
                         new FileOutputStream(copiedZip)) {

                if (in == null)
                    return "COPY FAILED\nContentResolver returned null.";

                byte[] buf = new byte[65536];
                int n;
                long total = 0;

                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }

                return "STAGE 2 PASSED\n\n"
                        + "Copied driver.zip internally.\n"
                        + "Bytes: " + total + "\n"
                        + "SHA256: " + sha256(copiedZip) + "\n\n"
                        + "Press 3. INSPECT DRIVER ZIP.";
            }

        } catch (Throwable t) {
            return throwable("COPY FAILED", t);
        }
    }

    public static String inspectZip(Context context) {
        if (copiedZip == null || !copiedZip.exists())
            return "NO INTERNAL ZIP\nRun Stage 2 first.";

        try {
            File driverDir = new File(context.getFilesDir(), "driver");
            deleteDir(driverDir);

            if (!driverDir.mkdirs() && !driverDir.isDirectory())
                return "INSPECT FAILED\nCould not create driver directory.";

            try (InputStream raw = new FileInputStream(copiedZip);
                 ZipInputStream zin = new ZipInputStream(raw)) {

                ZipEntry e;

                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;

                    String name = new File(e.getName()).getName();
                    if (name.isEmpty()) continue;

                    File outFile = new File(driverDir, name);

                    try (OutputStream out = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[65536];
                        int n;

                        while ((n = zin.read(buf)) > 0)
                            out.write(buf, 0, n);
                    }
                }
            }

            File meta = new File(driverDir, "meta.json");

            if (!meta.exists())
                return "INSPECT FAILED\nNo meta.json found.";

            JSONObject j = new JSONObject(readAll(meta));

            String libName = j.getString("libraryName");
            String name = j.optString("name", "?");
            String version = j.optString("driverVersion", "?");

            File lib = new File(driverDir, libName);

            if (!lib.exists()) {
                return "INSPECT FAILED\n\n"
                        + "meta.json libraryName:\n"
                        + libName
                        + "\n\nLibrary not found after extraction.";
            }

            stagedDir = driverDir;
            stagedLibrary = libName;
            stagedName = name;
            stagedVersion = version;

            return "STAGE 3 PASSED\n\n"
                    + "Driver: " + name + "\n"
                    + "Version: " + version + "\n"
                    + "Library: " + libName + "\n"
                    + "Library bytes: " + lib.length() + "\n\n"
                    + "Press 4. LOAD TURNIP + RUN PROBE.";

        } catch (Throwable t) {
            return throwable("INSPECT FAILED", t);
        }
    }

    public static String runProbe(Context context) {
        if (stagedDir == null || stagedLibrary == null)
            return "NO DRIVER STAGED\nRun Stage 3 first.";

        try {
            System.loadLibrary("driverhook");

            boolean ok = nativeInit(
                    context.getApplicationInfo().nativeLibraryDir,
                    stagedDir.getAbsolutePath() + "/",
                    stagedLibrary,
                    context.getCacheDir().getAbsolutePath()
            );

            if (!ok) {
                return "NATIVE INIT RETURNED FALSE\n\n"
                        + stagedName + "\n"
                        + stagedVersion + "\n"
                        + stagedLibrary;
            }

            return "STAGE 4: TURNIP ACTIVE\n\n"
                    + nativeProbe();

        } catch (Throwable t) {
            return throwable("NATIVE PROBE FAILED", t);
        }
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;

            while ((n = in.read(buf)) > 0)
                md.update(buf, 0, n);
        }

        StringBuilder s = new StringBuilder();

        for (byte b : md.digest())
            s.append(String.format("%02x", b));

        return s.toString();
    }

    private static String readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;

            while ((n = in.read(buf)) > 0)
                out.write(buf, 0, n);

            return out.toString("UTF-8");
        }
    }

    private static String throwable(String title, Throwable t) {
        StringBuilder s = new StringBuilder();

        s.append(title).append("\n\n");
        s.append(t.toString());

        for (StackTraceElement e : t.getStackTrace())
            s.append("\n  at ").append(e);

        return s.toString();
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) return;

        File[] children = f.listFiles();

        if (children != null)
            for (File c : children)
                deleteDir(c);

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
