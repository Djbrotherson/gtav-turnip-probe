package com.gtavsource.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {

    private TextView output;

    private Button b2;
    private Button b3;
    private Button b4;

    private File driverDir;
    private String driverName;
    private String driverLabel;
    private String driverVersion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 10");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button b1 = new Button(this);
        b1.setText("1. EXTRACT + VERIFY T28");

        b2 = new Button(this);
        b2.setText("2. LOAD PROBEONLY.SO");
        b2.setEnabled(false);

        b3 = new Button(this);
        b3.setText("3. ADRENOTOOLS LOAD T28");
        b3.setEnabled(false);

        b4 = new Button(this);
        b4.setText("4. CHECK SHADERINT64");
        b4.setEnabled(false);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);

        output.setText(
                "TEST 10\n\n"
              + "ByteHook has been completely removed from this test.\n\n"
              + "1. Extract T28\n"
              + "2. Load tiny native probe\n"
              + "3. Load T28 through AdrenoTools\n"
              + "4. Query Vulkan capabilities"
        );

        b1.setOnClickListener(v -> {
            String result = extractAndVerify();
            output.setText(result);

            if (result.startsWith("STAGE 1 PASSED")) {
                b2.setEnabled(true);
            }
        });

        b2.setOnClickListener(v -> {
            output.setText("Loading libprobeonly.so...\n");

            try {
                NativeProbe.loadLibrary();

                output.setText(
                        "STAGE 2 PASSED\n\n"
                      + "libprobeonly.so loaded.\n\n"
                      + "ByteHook is not linked into this library.\n"
                      + "Turnip has not been loaded yet."
                );

                b3.setEnabled(true);

            } catch (Throwable t) {
                output.setText(error("STAGE 2 FAILED", t));
            }
        });

        b3.setOnClickListener(v -> {
            output.setText(
                    "ENTERING ADRENOTOOLS...\n\n"
                  + "If the app dies here, AdrenoTools/custom ICD loading "
                  + "is the crash boundary."
            );

            try {
                boolean ok = NativeProbe.nativeInit(
                        getApplicationInfo().nativeLibraryDir,
                        driverDir.getAbsolutePath() + "/",
                        driverName,
                        getCacheDir().getAbsolutePath()
                );

                if (!ok) {
                    output.setText(
                            "STAGE 3 FAILED\n\n"
                          + "adrenotools_open_libvulkan returned false.\n\n"
                          + "Driver: " + driverLabel + "\n"
                          + "Version: " + driverVersion + "\n"
                          + "Library: " + driverName
                    );
                    return;
                }

                output.setText(
                        "STAGE 3 PASSED\n\n"
                      + "T28 loaded through AdrenoTools.\n\n"
                      + "Driver: " + driverLabel + "\n"
                      + "Version: " + driverVersion + "\n"
                      + "Library: " + driverName + "\n\n"
                      + "Press Stage 4."
                );

                b4.setEnabled(true);

            } catch (Throwable t) {
                output.setText(error("STAGE 3 FAILED", t));
            }
        });

        b4.setOnClickListener(v -> {
            output.setText("Querying T28 Vulkan capabilities...\n");

            try {
                output.setText(
                        "STAGE 4 RESULT\n\n"
                      + NativeProbe.nativeProbe()
                );
            } catch (Throwable t) {
                output.setText(error("STAGE 4 FAILED", t));
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(b1);
        root.addView(b2);
        root.addView(b3);
        root.addView(b4);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        setContentView(root);
    }

    private String extractAndVerify() {
        try {
            driverDir = new File(getFilesDir(), "driver");
            deleteDir(driverDir);

            if (!driverDir.mkdirs() && !driverDir.isDirectory()) {
                return "STAGE 1 FAILED\nCould not create driver directory.";
            }

            try (InputStream raw = getAssets().open("driver.zip");
                 ZipInputStream zin = new ZipInputStream(raw)) {

                ZipEntry e;

                while ((e = zin.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;

                    String name = new File(e.getName()).getName();
                    if (name.isEmpty()) continue;

                    try (OutputStream out =
                                 new FileOutputStream(
                                         new File(driverDir, name))) {

                        byte[] buf = new byte[65536];
                        int n;

                        while ((n = zin.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            }

            File meta = new File(driverDir, "meta.json");

            JSONObject j = new JSONObject(readAll(meta));

            driverName = j.getString("libraryName");
            driverLabel = j.optString("name", "?");
            driverVersion = j.optString("driverVersion", "?");

            File driver = new File(driverDir, driverName);

            if (!driver.exists()) {
                return "STAGE 1 FAILED\nDeclared library missing:\n"
                        + driverName;
            }

            return "STAGE 1 PASSED\n\n"
                    + "Driver: " + driverLabel + "\n"
                    + "Version: " + driverVersion + "\n"
                    + "Library: " + driverName + "\n"
                    + "Bytes: " + driver.length() + "\n\n"
                    + "Press Stage 2.";

        } catch (Throwable t) {
            return error("STAGE 1 FAILED", t);
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

        File[] children = f.listFiles();

        if (children != null) {
            for (File c : children) {
                deleteDir(c);
            }
        }

        f.delete();
    }

    private static String error(String title, Throwable t) {
        StringBuilder s = new StringBuilder();

        s.append(title).append("\n\n");
        s.append(t.toString());

        for (StackTraceElement e : t.getStackTrace()) {
            s.append("\n  at ").append(e);
        }

        return s.toString();
    }

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density
        );
    }
}
