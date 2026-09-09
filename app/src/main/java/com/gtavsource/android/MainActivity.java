package com.gtavsource.android;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = Math.round(
                14 * getResources().getDisplayMetrics().density);

        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 11");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button test = new Button(this);
        test.setText("1. DIRECTLY LOAD T28");

        TextView output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(14);
        output.setTextIsSelectable(true);

        output.setText(
                "TEST 11\n\n"
              + "No ByteHook.\n"
              + "No AdrenoTools.\n"
              + "No ZIP extraction at runtime.\n\n"
              + "The real T28 driver is packaged as libturnip.so.\n\n"
              + "This test performs:\n"
              + "dlopen(\"libturnip.so\")\n"
              + "dlsym(\"vkGetInstanceProcAddr\")"
        );

        test.setOnClickListener(v -> {
            try {
                output.setText(DirectLoadProbe.test());
            } catch (Throwable t) {
                StringBuilder s = new StringBuilder();

                s.append("JAVA/NATIVE FAILURE\n\n");
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
        root.addView(test);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f)
        );

        setContentView(root);
    }
}
