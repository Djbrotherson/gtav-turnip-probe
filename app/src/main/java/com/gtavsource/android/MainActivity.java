package com.gtavsource.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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

        int p = dp(12);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 5");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button stage1 = new Button(this);
        stage1.setText("1. VERIFY BUNDLED T28");

        stage2 = new Button(this);
        stage2.setText("2. EXTRACT + VALIDATE T28");
        stage2.setEnabled(false);

        stage3 = new Button(this);
        stage3.setText("3. LOAD TURNIP");
        stage3.setEnabled(false);

        stage4 = new Button(this);
        stage4.setText("4. RUN CAPABILITY PROBE");
        stage4.setEnabled(false);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setText(
                "T28 toasted is bundled inside this APK.\n\n"
              + "No file picker.\n"
              + "No external storage.\n"
              + "No native code until Stage 3.\n\n"
              + "Press Stage 1."
        );

        stage1.setOnClickListener(v -> runAsync(() ->
                DriverBootstrap.verifyAsset(this),
                2));

        stage2.setOnClickListener(v -> runAsync(() ->
                DriverBootstrap.extractDriver(this),
                3));

        stage3.setOnClickListener(v -> runAsync(() ->
                DriverBootstrap.loadTurnip(this),
                4));

        stage4.setOnClickListener(v -> runAsync(
                DriverBootstrap::runCapabilityProbe,
                0));

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

    private interface Work {
        String run();
    }

    private void runAsync(Work work, int nextStage) {
        output.setText("Working...\n");

        new Thread(() -> {
            String result = work.run();

            runOnUiThread(() -> {
                output.setText(result);

                if (result.contains("PASSED")) {
                    if (nextStage == 2) stage2.setEnabled(true);
                    if (nextStage == 3) stage3.setEnabled(true);
                    if (nextStage == 4) stage4.setEnabled(true);
                }
            });
        }).start();
    }

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density
        );
    }
}
