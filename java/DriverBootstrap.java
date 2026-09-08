package com.gtavsource.android;

import android.app.Application;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runs before any activity, so the hooks are in place long before libgtav.so
 * asks DXVK to spin up.
 *
 * Driver selection is driven by the package's own meta.json rather than a
 * hard-coded filename. Mesa/StevenMX builds use libvulkan_freedreno.so;
 * Mr_Purple "toasted" builds use vulkan.purple.so. Hard-coding one and then
 * testing the other fails silently, so we read the field instead.
 *
 * Usage: drop an unmodified .adpkg.zip (or plain driver zip) at
 *
 *     /sdcard/turnip/driver.zip
 *
 * and it is unpacked into the app's internal files dir on first run. The
 * internal copy is mandatory -- since Android 10 you cannot dlopen a library
 * that lives on external storage.
 *
 * To switch drivers: replace driver.zip, force-stop the app, relaunch. No
 * rebuild, no reinstall.
 */
public class DriverBootstrap extends Application {

    private static final String TAG = "DriverBootstrap";
    private static final String SOURCE_ZIP = "/sdcard/turnip/driver.zip";

    static {
        System.loadLibrary("driverhook");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        File driverDir = new File(getFilesDir(), "driver");
        File stamp = new File(driverDir, ".source-sha256");

        try {
            File src = new File(SOURCE_ZIP);
            if (src.exists()) {
                String digest = sha256(src);
                if (!alreadyUnpacked(stamp, digest)) {
                    deleteDir(driverDir);
                    if (!driverDir.mkdirs()) throw new Exception("mkdir " + driverDir);
                    unzip(src, driverDir);
                    writeStamp(stamp, digest);
                    Log.i(TAG, "unpacked driver package, sha256=" + digest.substring(0, 16));
                } else {
                    Log.i(TAG, "driver package unchanged, reusing extracted copy");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "staging failed", e);
        }

        File meta = new File(driverDir, "meta.json");
        if (!meta.exists()) {
            Log.e(TAG, "no meta.json in " + driverDir + " -- put an .adpkg.zip at "
                     + SOURCE_ZIP + ". Falling back to the stock driver.");
            return;
        }

        String libName, driverLabel;
        try {
            JSONObject j = new JSONObject(readAll(meta));
            libName = j.getString("libraryName");
            driverLabel = j.optString("name", "?") + " / " + j.optString("driverVersion", "?");
        } catch (Exception e) {
            Log.e(TAG, "meta.json unreadable", e);
            return;
        }

        File lib = new File(driverDir, libName);
        if (!lib.exists()) {
            Log.e(TAG, "meta.json names " + libName + " but it is not in the package");
            return;
        }

        Log.i(TAG, "driver: " + driverLabel + " (" + libName + ", "
                 + lib.length() + " bytes)");

        boolean ok = nativeInit(
                getApplicationInfo().nativeLibraryDir,
                driverDir.getAbsolutePath() + "/",
                libName,
                getCacheDir().getAbsolutePath());

        if (!ok) {
            Log.e(TAG, "driver load FAILED, stock driver in use");
            return;
        }

        Log.i(TAG, "custom driver active");

        // Comment out once you are done probing and just want to play.
        for (String l : nativeProbe().split("\n")) Log.i("probe", l);
    }

    // --- plumbing ------------------------------------------------------------

    /**
     * Content hash, not size. Two Turnip packages colliding on byte length is
     * unlikely but would silently reuse the wrong extracted driver, which is
     * exactly the failure you cannot see in a log.
     */
    private String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private boolean alreadyUnpacked(File stamp, String digest) {
        try {
            return stamp.exists() && readAll(stamp).trim().equals(digest);
        } catch (Exception e) {
            return false;
        }
    }

    private void writeStamp(File stamp, String digest) throws Exception {
        try (OutputStream o = new FileOutputStream(stamp)) {
            o.write(digest.getBytes("UTF-8"));
        }
    }

    private void unzip(File zip, File dest) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = new File(e.getName()).getName();   // flatten, no traversal
                if (name.isEmpty()) continue;
                try (OutputStream out = new FileOutputStream(new File(dest, name))) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
    }

    private String readAll(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private void deleteDir(File d) {
        File[] kids = d.listFiles();
        if (kids != null) for (File k : kids) k.delete();
        d.delete();
    }

    private static native boolean nativeInit(String hookLibDir,
                                             String driverDir,
                                             String driverName,
                                             String tmpDir);

    private static native String nativeProbe();
}
