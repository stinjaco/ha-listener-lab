package com.listenerlab.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private EditText address;
    private EditText token;
    private Button scanButton;
    private Button copyButton;
    private Button openButton;
    private ProgressBar progress;
    private TextView report;
    private String latestReport = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        int pad = dp(20);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(Color.rgb(7, 21, 31));

        TextView title = text("Listener Lab", 30, Color.rgb(97, 208, 149));
        title.setTypeface(null, 1);
        page.addView(title);

        TextView subtitle = text("Find out what Home Assistant is listening through.", 17, Color.rgb(236, 247, 243));
        subtitle.setPadding(0, dp(4), 0, dp(18));
        page.addView(subtitle);

        TextView privacy = text("READ-ONLY SCAN  •  TOKEN IS NOT SAVED", 12, Color.rgb(175, 194, 198));
        privacy.setPadding(0, 0, 0, dp(12));
        page.addView(privacy);

        address = input("Home Assistant address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("http://homeassistant.local:8123");
        page.addView(address, fullWidth(dp(56)));

        token = input("Long-lived access token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("Paste token (kept in memory only)");
        LinearLayout.LayoutParams tokenParams = fullWidth(dp(56));
        tokenParams.topMargin = dp(10);
        page.addView(token, tokenParams);

        TextView help = text("Create the token in Home Assistant: profile icon → Security → Long-lived access tokens. Use a temporary admin token for the fullest inventory, then delete it after the scan.", 13, Color.rgb(175, 194, 198));
        help.setPadding(0, dp(9), 0, dp(14));
        page.addView(help);

        scanButton = button("ANALYZE SYSTEM");
        scanButton.setOnClickListener(view -> startScan());
        page.addView(scanButton, fullWidth(dp(56)));

        openButton = button("OPEN HOME ASSISTANT");
        openButton.setOnClickListener(view -> openHomeAssistant());
        LinearLayout.LayoutParams openParams = fullWidth(dp(50));
        openParams.topMargin = dp(10);
        page.addView(openButton, openParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(12);
        page.addView(progress, progressParams);

        copyButton = button("COPY REPORT");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(view -> copyReport());
        LinearLayout.LayoutParams copyParams = fullWidth(dp(50));
        copyParams.topMargin = dp(12);
        page.addView(copyButton, copyParams);

        report = text("Connect to Home Assistant, then tap Analyze System. Nothing will be changed.", 15, Color.rgb(236, 247, 243));
        report.setTextIsSelectable(true);
        report.setLineSpacing(0, 1.15f);
        report.setPadding(dp(15), dp(15), dp(15), dp(15));
        report.setBackgroundColor(Color.rgb(16, 39, 52));
        LinearLayout.LayoutParams reportParams = fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        reportParams.topMargin = dp(14);
        page.addView(report, reportParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private void startScan() {
        setBusy(true);
        latestReport = "";
        report.setText("Connecting and reading the system inventory…");
        new HomeAssistantScanner().scan(address.getText().toString(), token.getText().toString(),
                new HomeAssistantScanner.ResultCallback() {
                    @Override public void onSuccess(String value) {
                        runOnUiThread(() -> {
                            latestReport = value;
                            report.setText(value);
                            copyButton.setEnabled(true);
                            setBusy(false);
                        });
                    }

                    @Override public void onFailure(String message) {
                        runOnUiThread(() -> {
                            report.setText("SCAN COULD NOT FINISH\n\n" + message + "\n\nNo settings were changed.");
                            setBusy(false);
                        });
                    }
                });
    }

    private void setBusy(boolean busy) {
        scanButton.setEnabled(!busy);
        address.setEnabled(!busy);
        token.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Listener Lab report", latestReport));
        Toast.makeText(this, "Report copied — the token is not included.", Toast.LENGTH_SHORT).show();
    }

    private void openHomeAssistant() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Enter the Home Assistant address first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (Exception error) {
            Toast.makeText(this, "No app could open that address.", Toast.LENGTH_SHORT).show();
        }
    }

    private EditText input(String description, int inputType) {
        EditText field = new EditText(this);
        field.setContentDescription(description);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextColor(Color.rgb(236, 247, 243));
        field.setHintTextColor(Color.rgb(130, 154, 160));
        field.setBackgroundColor(Color.rgb(16, 39, 52));
        field.setPadding(dp(14), 0, dp(14), 0);
        return field;
    }

    private Button button(String label) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(16);
        value.setTextColor(Color.rgb(236, 247, 243));
        value.setBackgroundColor(Color.rgb(23, 62, 49));
        value.setAllCaps(false);
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
