package com.gtavsource.android;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

public class MainActivity extends Activity {

    private TextView output;
    private Button assetButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 6");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button uiButton = new Button(this);
        uiButton.setText("1. UI-ONLY TEST");

        assetButton = new Button(this);
        assetButton.setText("2. READ 16 BYTES FROM T28");
        assetButton.setEnabled(false);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(14);
        output.setTextIsSelectable(true);
        output.setText(
                "TEST 6\n\n"
              + "Button 1 does absolutely nothing except change this text.\n\n"
              + "No DriverBootstrap.\n"
              + "No JNI.\n"
              + "No Turnip.\n"
              + "No ZIP parsing."
        );

        uiButton.setOnClickListener(v -> {
            output.setText(
                    "STAGE 1 PASSED\n\n"
                  + "The Activity and button handler are stable.\n\n"
                  + "No file was opened.\n"
                  + "No native code was touched.\n\n"
                  + "You may now press button 2."
            );

            assetButton.setEnabled(true);
        });

        assetButton.setOnClickListener(v -> {
            try {
                byte[] b = new byte[16];
                int n;

                try (InputStream in =
                             getAssets().open("driver.zip")) {
                    n = in.read(b);
                }

                StringBuilder hex = new StringBuilder();

                for (int i = 0; i < n; i++) {
                    hex.append(String.format("%02x ", b[i]));
                }

                output.setText(
                        "STAGE 2 PASSED\n\n"
                      + "Bundled driver.zip opened successfully.\n"
                      + "Bytes read: " + n + "\n\n"
                      + "First bytes:\n"
                      + hex.toString()
                );

            } catch (Throwable t) {
                StringBuilder s = new StringBuilder();

                s.append("STAGE 2 JAVA ERROR\n\n");
                s.append(t.toString());

                for (StackTraceElement e : t.getStackTrace()) {
                    s.append("\n  at ").append(e);
                }

                output.setText(s.toString());
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(uiButton);
        root.addView(assetButton);

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

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density
        );
    }
}
