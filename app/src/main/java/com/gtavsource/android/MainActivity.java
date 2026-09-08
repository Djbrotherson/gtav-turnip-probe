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
    private Button stage2;
    private Button stage3;
    private Button stage4;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 8");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button stage1 = new Button(this);
        stage1.setText("1. READ 16 BYTES FROM T28");

        stage2 = new Button(this);
        stage2.setText("2. EXTRACT T28");
        stage2.setEnabled(false);

        stage3 = new Button(this);
        stage3.setText("3. VERIFY META + DRIVER LIB");
        stage3.setEnabled(false);

        stage4 = new Button(this);
        stage4.setText("4. LOAD DRIVERHOOK.SO ONLY");
        stage4.setEnabled(false);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setText(
                "TEST 8\n\n"
              + "Stages 1-3 repeat the proven Java path.\n\n"
              + "Stage 4 ONLY calls:\n"
              + "System.loadLibrary(\"driverhook\")\n\n"
              + "No nativeInit.\n"
              + "No Turnip loading.\n"
              + "No Vulkan calls."
        );

        stage1.setOnClickListener(v -> {
            try {
                byte[] b = new byte[16];
                int n;

                try (InputStream in = getAssets().open("driver.zip")) {
                    n = in.read(b);
                }

                StringBuilder hex = new StringBuilder();

                for (int i = 0; i < n; i++) {
                    hex.append(String.format("%02x ", b[i]));
                }

                output.setText(
                        "STAGE 1 PASSED\n\n"
                      + "Bundled driver.zip opened successfully.\n"
                      + "Bytes read: " + n + "\n\n"
                      + "First bytes:\n"
                      + hex
                );

                stage2.setEnabled(true);

            } catch (Throwable t) {
                output.setText(formatError("STAGE 1 FAILED", t));
            }
        });

        stage2.setOnClickListener(v -> {
            output.setText("Extracting T28...\n");

            new Thread(() -> {
                String result = extractDriver();

                runOnUiThread(() -> {
                    output.setText(result);

                    if (result.startsWith("STAGE 2 PASSED")) {
                        stage3.setEnabled(true);
                    }
                });
            }).start();
        });

        stage3.setOnClickListener(v -> {
            output.setText("Verifying metadata and driver library...\n");

            new Thread(() -> {
                String result = verifyDriver();

                runOnUiThread(() -> {
                    output.setText(result);

                    if (result.startsWith("STAGE 3 PASSED")) {
                        stage4.setEnabled(true);
                    }
                });
            }).start();
        });

        stage4.setOnClickListener(v -> {
            output.setText(
                    "ENTERING SYSTEM.LOADLIBRARY...\n\n"
                  + "Loading libdriverhook.so only.\n"
                  + "No JNI function will be called."
            );

            try {
                System.loadLibrary("driverhook");

                output.setText(
                        "STAGE 4 PASSED\n\n"
                      + "libdriverhook.so loaded successfully.\n\n"
                      + "This proves Android can load the complete native shim "
                      + "and its shared-library dependencies.\n\n"
                      + "No nativeInit was called.\n"
                      + "No Turnip driver was loaded.\n"
                      + "No Vulkan API was touched."
                );

            } catch (Throwable t) {
                output.setText(formatError("STAGE 4 JAVA/NATIVE LOAD ERROR", t));
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(stage1);
        root.addView(stage2);
        root.addView(stage3);
        root.addView(stage4);

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

    private String extractDriver() {
        try {
            File dir = new File(getFilesDir(), "driver");
            deleteDir(dir);

            if (!dir.mkdirs() && !dir.isDirectory()) {
                return "STAGE 2 FAILED\nCould not create driver directory.";
            }

            int files = 0;
            long bytes = 0;

            try (InputStream raw = getAssets().open("driver.zip");
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
                            bytes += n;
                        }
                    }

                    files++;
                }
            }

            return "STAGE 2 PASSED\n\n"
                    + "Extracted files: " + files + "\n"
                    + "Extracted bytes: " + bytes + "\n\n"
                    + "No native code loaded.";

        } catch (Throwable t) {
            return formatError("STAGE 2 FAILED", t);
        }
    }

    private String verifyDriver() {
        try {
            File dir = new File(getFilesDir(), "driver");
            File meta = new File(dir, "meta.json");

            if (!meta.exists()) {
                return "STAGE 3 FAILED\nmeta.json not found.";
            }

            JSONObject j = new JSONObject(readAll(meta));

            String libraryName = j.getString("libraryName");
            String name = j.optString("name", "?");
            String version = j.optString("driverVersion", "?");

            File lib = new File(dir, libraryName);

            if (!lib.exists()) {
                return "STAGE 3 FAILED\n\n"
                        + "Driver: " + name + "\n"
                        + "Version: " + version + "\n"
                        + "Library: " + libraryName + "\n\n"
                        + "Declared library not found.";
            }

            return "STAGE 3 PASSED\n\n"
                    + "Driver: " + name + "\n"
                    + "Version: " + version + "\n"
                    + "Library: " + libraryName + "\n"
                    + "Library bytes: " + lib.length() + "\n\n"
                    + "Java-side handling is good.\n\n"
                    + "Press Stage 4.";

        } catch (Throwable t) {
            return formatError("STAGE 3 FAILED", t);
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

    private static String formatError(String title, Throwable t) {
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
