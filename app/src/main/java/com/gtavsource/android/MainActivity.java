package com.gtavsource.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PICK_DRIVER = 1001;

    private TextView output;
    private Button pickButton;
    private Button probeButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Capability Probe - Test 2");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        pickButton = new Button(this);
        pickButton.setText("1. SELECT TURNIP DRIVER");
        pickButton.setOnClickListener(v -> chooseDriver());

        probeButton = new Button(this);
        probeButton.setText("2. LOAD TURNIP + RUN PROBE");
        probeButton.setEnabled(false);
        probeButton.setOnClickListener(v -> runNativeProbe());

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setText(
                "Step 1 only unpacks and validates the driver.\n\n"
              + "No Vulkan driver is loaded until Step 2."
        );

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(pickButton);
        root.addView(probeButton);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );

        root.addView(scroll, scrollParams);

        setContentView(root);
    }

    private void chooseDriver() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_DRIVER);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);

        if (request != PICK_DRIVER ||
            result != RESULT_OK ||
            data == null ||
            data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        pickButton.setEnabled(false);
        output.setText("Staging driver...\n");

        new Thread(() -> {
            String report = DriverBootstrap.stageDriver(this, uri);

            runOnUiThread(() -> {
                output.setText(report);
                pickButton.setEnabled(true);

                if (report.startsWith("STAGE 1 PASSED")) {
                    probeButton.setEnabled(true);
                }
            });
        }).start();
    }

    private void runNativeProbe() {
        probeButton.setEnabled(false);
        pickButton.setEnabled(false);

        output.append(
                "\n\n================================\n"
              + "ENTERING NATIVE TURNIP LOADER...\n"
              + "If the app dies now, nativeInit is the crash point.\n"
              + "================================\n"
        );

        new Thread(() -> {
            String report = DriverBootstrap.runProbe(this);

            runOnUiThread(() -> {
                output.setText(report);
                probeButton.setEnabled(true);
                pickButton.setEnabled(true);
            });
        }).start();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
