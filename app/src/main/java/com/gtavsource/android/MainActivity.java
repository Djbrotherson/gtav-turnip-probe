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

    private Uri selectedUri;

    private TextView output;
    private Button copyButton;
    private Button inspectButton;
    private Button probeButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(12);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 4");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button pickButton = new Button(this);
        pickButton.setText("1. SELECT DRIVER ZIP");
        pickButton.setOnClickListener(v -> selectZip());

        copyButton = new Button(this);
        copyButton.setText("2. COPY ZIP INTERNALLY");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> copyZip());

        inspectButton = new Button(this);
        inspectButton.setText("3. INSPECT DRIVER ZIP");
        inspectButton.setEnabled(false);
        inspectButton.setOnClickListener(v -> inspectZip());

        probeButton = new Button(this);
        probeButton.setText("4. LOAD TURNIP + RUN PROBE");
        probeButton.setEnabled(false);
        probeButton.setOnClickListener(v -> runProbe());

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);

        output.setText(
                "TEST 4\n\n"
              + "Selecting a file performs ZERO file IO.\n"
              + "No ZIP extraction.\n"
              + "No native libraries.\n"
              + "No Turnip.\n\n"
              + "Start with button 1."
        );

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);

        root.addView(title);
        root.addView(pickButton);
        root.addView(copyButton);
        root.addView(inspectButton);
        root.addView(probeButton);

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

    private void selectZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, PICK_DRIVER);
    }

    @Override
    protected void onActivityResult(
            int request,
            int result,
            Intent data) {

        super.onActivityResult(request, result, data);

        if (request != PICK_DRIVER)
            return;

        if (result != RESULT_OK ||
            data == null ||
            data.getData() == null) {

            output.setText("FILE PICKER RETURNED WITHOUT A FILE.");
            return;
        }

        selectedUri = data.getData();

        output.setText(
                "STAGE 1 PASSED\n\n"
              + "Android file picker returned successfully.\n\n"
              + "URI:\n"
              + selectedUri.toString()
              + "\n\nNo file has been opened yet.\n"
              + "Press button 2."
        );

        copyButton.setEnabled(true);
    }

    private void copyZip() {
        output.setText("Copying selected file...\n");

        new Thread(() -> {
            String r =
                    DriverBootstrap.copyZip(this, selectedUri);

            runOnUiThread(() -> {
                output.setText(r);

                if (r.startsWith("STAGE 2 PASSED"))
                    inspectButton.setEnabled(true);
            });
        }).start();
    }

    private void inspectZip() {
        output.setText("Opening ZIP and reading metadata...\n");

        new Thread(() -> {
            String r =
                    DriverBootstrap.inspectZip(this);

            runOnUiThread(() -> {
                output.setText(r);

                if (r.startsWith("STAGE 3 PASSED"))
                    probeButton.setEnabled(true);
            });
        }).start();
    }

    private void runProbe() {
        output.setText(
                "ENTERING NATIVE LOADER...\n\n"
              + "If the app dies now, the native loader is responsible."
        );

        new Thread(() -> {
            String r =
                    DriverBootstrap.runProbe(this);

            runOnUiThread(() -> output.setText(r));
        }).start();
    }

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density
        );
    }
}
