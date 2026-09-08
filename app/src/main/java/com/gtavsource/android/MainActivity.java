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

    private Button b2;
    private Button b3;
    private Button b4;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(14);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("GTAV Turnip Probe - Test 9");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button b1 = new Button(this);
        b1.setText("1. LOAD LIBBYTEHOOK.SO");

        b2 = new Button(this);
        b2.setText("2. LOAD LIBHOOK_IMPL.SO");
        b2.setEnabled(false);

        b3 = new Button(this);
        b3.setText("3. LOAD LIBMAIN_HOOK.SO");
        b3.setEnabled(false);

        b4 = new Button(this);
        b4.setText("4. LOAD LIBDRIVERHOOK.SO");
        b4.setEnabled(false);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(14);
        output.setTextIsSelectable(true);

        output.setText(
                "TEST 9\n\n"
              + "No T28 access.\n"
              + "No ZIP extraction.\n"
              + "No nativeInit.\n"
              + "No Vulkan.\n\n"
              + "We are loading each native library separately.\n\n"
              + "Press button 1 only."
        );

        b1.setOnClickListener(v ->
                loadOne("bytehook", "libbytehook.so", b2));

        b2.setOnClickListener(v ->
                loadOne("hook_impl", "libhook_impl.so", b3));

        b3.setOnClickListener(v ->
                loadOne("main_hook", "libmain_hook.so", b4));

        b4.setOnClickListener(v ->
                loadOne("driverhook", "libdriverhook.so", null));

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

    private void loadOne(
            String loadName,
            String displayName,
            Button next) {

        output.setText(
                "LOADING " + displayName + "...\n\n"
              + "If the app disappears now, this library is the crash boundary."
        );

        try {
            System.loadLibrary(loadName);

            output.setText(
                    "PASSED\n\n"
                  + displayName
                  + " loaded successfully.\n\n"
                  + "No JNI function was called."
            );

            if (next != null) {
                next.setEnabled(true);
            }

        } catch (Throwable t) {
            StringBuilder s = new StringBuilder();

            s.append("JAVA LOAD ERROR\n\n");
            s.append(displayName).append("\n\n");
            s.append(t.toString());

            for (StackTraceElement e : t.getStackTrace()) {
                s.append("\n  at ").append(e);
            }

            output.setText(s.toString());
        }
    }

    private int dp(int n) {
        return Math.round(
                n * getResources().getDisplayMetrics().density
        );
    }
}
