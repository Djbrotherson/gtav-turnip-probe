package com.gtavsource.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PICK_DRIVER = 1001;

    private TextView output;
    private Button button;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Capability Probe");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView explanation = new TextView(this);
        explanation.setText(
                "\nSelect an unmodified Turnip driver ZIP.\n\n"
              + "Start with:\n"
              + "turnip_mrpurple_T28-toasted.adpkg.zip\n\n"
              + "The driver will be copied into this app's private storage "
              + "and loaded through AdrenoTools."
        );
        explanation.setTextSize(15);

        button = new Button(this);
        button.setText("SELECT TURNIP DRIVER");
        button.setOnClickListener(v -> chooseDriver());

        output = new TextView(this);
        output.setText(
                "Waiting for driver.\n\n"
              + "Important result:\n"
              + "shaderInt64 = true or false"
        );
        output.setTextSize(13);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(explanation);
        root.addView(button);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );

        scrollParams.topMargin = dp(12);

        root.addView(scroll, scrollParams);

        setContentView(root);
    }

    private void chooseDriver() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");

        // Some Android file managers don't tag .adpkg.zip correctly.
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip",
                "application/octet-stream",
                "*/*"
        });

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

        button.setEnabled(false);
        button.setText("RUNNING PROBE...");
        output.setText("Loading Turnip driver...\n");

        new Thread(() -> {
            String report = DriverBootstrap.runProbe(this, uri);

            runOnUiThread(() -> {
                output.setText(report);
                button.setEnabled(true);
                button.setText("SELECT ANOTHER DRIVER");
            });
        }).start();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
