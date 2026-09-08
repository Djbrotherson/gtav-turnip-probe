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

    private DriverBootstrap() {}

    public static String runProbe(Context context, Uri driverPackage) {
        try {
            File driverDir = new File(context.getFilesDir(), "driver");

            deleteDir(driverDir);

            if (!driverDir.mkdirs() && !driverDir.isDirectory()) {
                return "ERROR\nCould not create internal driver directory.";
            }

            try (InputStream in =
                         context.getContentResolver().openInputStream(driverPackage)) {

                if (in == null) {
                    return "ERROR\nCould not open selected driver package.";
                }

                unzip(in, driverDir);
            }

            File meta = new File(driverDir, "meta.json");

            if (!meta.exists()) {
                return "ERROR\nSelected ZIP does not contain meta.json.";
            }

            JSONObject j = new JSONObject(readAll(meta));

            String libraryName = j.getString("libraryName");
            String name = j.optString("name", "?");
            String version = j.optString("driverVersion", "?");

            File driver = new File(driverDir, libraryName);

            if (!driver.exists()) {
                return "ERROR\nmeta.json specifies:\n"
                        + libraryName
                        + "\n\nbut that library was not found in the ZIP.";
            }

            String nativeDir =
                    context.getApplicationInfo().nativeLibraryDir;

            boolean ok = nativeInit(
                    nativeDir,
                    driverDir.getAbsolutePath() + "/",
                    libraryName,
                    context.getCacheDir().getAbsolutePath()
            );

            if (!ok) {
                return "TURNIP LOAD FAILED\n\n"
                        + "Driver: " + name + "\n"
                        + "Version: " + version + "\n"
                        + "Library: " + libraryName + "\n\n"
                        + "The AdrenoTools Vulkan loader did not initialize.";
            }

            String report = nativeProbe();

            return "CUSTOM DRIVER ACTIVE\n\n"
                    + "Package: " + name + "\n"
                    + "Version: " + version + "\n"
                    + "Library: " + libraryName + "\n\n"
                    + report;

        } catch (Throwable t) {
            StringBuilder s = new StringBuilder();

            s.append("PROBE FAILED\n\n");
            s.append(t.toString()).append("\n");

            for (StackTraceElement e : t.getStackTrace()) {
                s.append("\n  at ").append(e.toString());
            }

            return s.toString();
        }
    }

    private static void unzip(InputStream raw, File dest) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(raw)) {
            ZipEntry e;

            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;

                // Driver packages sometimes put files inside folders.
                // Flatten them because meta.json refers to the library basename.
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
            for (File k : kids) {
                deleteDir(k);
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
