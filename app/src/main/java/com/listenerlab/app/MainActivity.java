package com.listenerlab.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
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
    private static final String DESKTOP_URL = "https://stinjaco.github.io/ha-listener-lab/desktop/";
    private EditText address;
    private EditText token;
    private LinearLayout manualPanel;
    private Button scanButton;
    private Button copyButton;
    private Button desktopButton;
    private ProgressBar progress;
    private TextView status;
    private TextView report;
    private SignalVisualizerView visualizer;
    private HomeAssistantDiscovery discovery;
    private String latestReport = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String previousCrash = CrashReporter.read(this);
        if (!previousCrash.isEmpty()) {
            setContentView(buildRecoveryScreen(previousCrash));
            return;
        }
        startNormalScreen();
    }

    private void startNormalScreen() {
        setContentView(buildScreen());
        Uri callback = getIntent().getData();
        if (isOAuthRedirect(callback)) handleOAuthRedirect(callback);
        else prepareConnection();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri callback = intent.getData();
        if (isOAuthRedirect(callback)) handleOAuthRedirect(callback);
    }

    @Override protected void onDestroy() {
        if (discovery != null) discovery.stop();
        super.onDestroy();
    }

    private View buildScreen() {
        int pad = dp(18);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, dp(32));
        page.setBackgroundColor(Color.rgb(5, 16, 24));

        TextView eyebrow = text("HOME ASSISTANT / VOICE SYSTEM INTAKE", 11, 0xFF61D095);
        eyebrow.setLetterSpacing(0.12f);
        page.addView(eyebrow);

        TextView title = text("LISTENER LAB", 29, 0xFFECF7F3);
        title.setTypeface(null, 1);
        title.setLetterSpacing(0.05f);
        page.addView(title);

        TextView subtitle = text("Map the listeners. Isolate the weak link. Change nothing until it is identified.", 14, 0xFFAFC2C6);
        subtitle.setPadding(0, dp(3), 0, dp(14));
        page.addView(subtitle);

        visualizer = new SignalVisualizerView(this);
        visualizer.setBackground(panelBackground(0xFF0A1A24, 0xFF23414C));
        page.addView(visualizer, fullWidth(dp(190)));

        status = text("STANDBY / WAITING FOR LOCAL DISCOVERY", 12, 0xFFFFD166);
        status.setLetterSpacing(0.08f);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(panelBackground(0xFF102734, 0xFF23414C));
        LinearLayout.LayoutParams statusParams = fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(8);
        page.addView(status, statusParams);

        TextView serverLabel = text("HOME ASSISTANT INSTANCE", 11, 0xFF61D095);
        serverLabel.setPadding(0, dp(16), 0, dp(6));
        page.addView(serverLabel);

        address = input("Home Assistant address", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("http://homeassistant.local:8123");
        page.addView(address, fullWidth(dp(54)));

        Button findButton = secondaryButton("FIND LOCAL HOME ASSISTANT");
        findButton.setOnClickListener(view -> startLocalDiscovery());
        LinearLayout.LayoutParams findParams = fullWidth(dp(46));
        findParams.topMargin = dp(8);
        page.addView(findButton, findParams);

        scanButton = primaryButton("CONNECT + ANALYZE");
        scanButton.setOnClickListener(view -> connectAndAnalyze());
        LinearLayout.LayoutParams scanParams = fullWidth(dp(58));
        scanParams.topMargin = dp(12);
        page.addView(scanButton, scanParams);

        Button manualButton = secondaryButton("USE MANUAL TOKEN");
        manualButton.setOnClickListener(view -> {
            boolean show = manualPanel.getVisibility() != View.VISIBLE;
            manualPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            manualButton.setText(show ? "HIDE MANUAL TOKEN" : "USE MANUAL TOKEN");
        });
        LinearLayout.LayoutParams manualButtonParams = fullWidth(dp(46));
        manualButtonParams.topMargin = dp(8);
        page.addView(manualButton, manualButtonParams);

        manualPanel = new LinearLayout(this);
        manualPanel.setOrientation(LinearLayout.VERTICAL);
        manualPanel.setVisibility(View.GONE);
        token = input("Long-lived access token", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint("Advanced fallback: paste temporary token");
        manualPanel.addView(token, fullWidth(dp(52)));
        TextView tokenHelp = text("OAuth is preferred. A pasted token stays in memory only and is never included in the report.", 12, 0xFFAFC2C6);
        tokenHelp.setPadding(0, dp(6), 0, 0);
        manualPanel.addView(tokenHelp);
        LinearLayout.LayoutParams manualParams = fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        manualParams.topMargin = dp(8);
        page.addView(manualPanel, manualParams);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(10);
        page.addView(progress, progressParams);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setWeightSum(2f);
        Button openButton = secondaryButton("OPEN HA");
        openButton.setOnClickListener(view -> openHomeAssistant());
        actionRow.addView(openButton, weightedButton());
        copyButton = secondaryButton("COPY REPORT");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(view -> copyReport());
        LinearLayout.LayoutParams copyActionParams = weightedButton();
        copyActionParams.leftMargin = dp(8);
        actionRow.addView(copyButton, copyActionParams);
        LinearLayout.LayoutParams rowParams = fullWidth(dp(48));
        rowParams.topMargin = dp(10);
        page.addView(actionRow, rowParams);

        desktopButton = warningButton("DESKTOP FIRMWARE RECOVERY");
        desktopButton.setVisibility(View.GONE);
        desktopButton.setOnClickListener(view -> showDesktopGate());
        LinearLayout.LayoutParams desktopParams = fullWidth(dp(54));
        desktopParams.topMargin = dp(10);
        page.addView(desktopButton, desktopParams);

        report = text("The intake report will appear here after secure connection and read-only inventory.", 14, 0xFFECF7F3);
        report.setTextIsSelectable(true);
        report.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        report.setMovementMethod(LinkMovementMethod.getInstance());
        report.setLineSpacing(0, 1.18f);
        report.setPadding(dp(14), dp(14), dp(14), dp(14));
        report.setBackground(panelBackground(0xFF0A1A24, 0xFF23414C));
        LinearLayout.LayoutParams reportParams = fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        reportParams.topMargin = dp(12);
        page.addView(report, reportParams);

        Button forgetButton = secondaryButton("FORGET SAVED CONNECTION");
        forgetButton.setOnClickListener(view -> {
            OAuthManager.forget(this);
            token.setText("");
            status.setText("SAVED AUTHORIZATION CLEARED");
            Toast.makeText(this, "Listener Lab connection removed.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams forgetParams = fullWidth(dp(44));
        forgetParams.topMargin = dp(10);
        page.addView(forgetButton, forgetParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private void prepareConnection() {
        String saved = OAuthManager.savedServer(this);
        if (!saved.isEmpty()) {
            address.setText(saved);
            status.setText("SAVED CONNECTION FOUND / READY TO ANALYZE");
            return;
        }
        address.setText("http://homeassistant.local:8123");
        status.setText("SAFE START / STANDARD ADDRESS READY");
        visualizer.setScanning(false);
    }

    private void startLocalDiscovery() {
        if (discovery != null) discovery.stop();
        status.setText("SEARCHING LOCAL NETWORK FOR HOME ASSISTANT");
        visualizer.setScanning(true);
        discovery = new HomeAssistantDiscovery(this, new HomeAssistantDiscovery.Callback() {
            @Override public void onFound(String displayName, String url) {
                runOnUiThread(() -> {
                    if (address.getText().toString().trim().isEmpty()) address.setText(url);
                    status.setText("DISCOVERED / " + displayName.toUpperCase());
                    visualizer.setScanning(false);
                });
            }

            @Override public void onFinishedWithoutResult() {
                runOnUiThread(() -> {
                    if (address.getText().toString().trim().isEmpty()) {
                        address.setText("http://homeassistant.local:8123");
                        status.setText("NO BROADCAST FOUND / TRYING STANDARD ADDRESS");
                    }
                    visualizer.setScanning(false);
                });
            }
        });
        try {
            discovery.start();
        } catch (RuntimeException error) {
            status.setText("DISCOVERY UNAVAILABLE / USE THE ADDRESS FIELD");
            visualizer.setScanning(false);
            report.setText("Local discovery is not supported by this phone or network. The standard homeassistant.local address is still ready, or you can enter the address shown in the Home Assistant Companion app.");
        }
    }

    private View buildRecoveryScreen(String diagnostic) {
        int pad = dp(20);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(0xFF051018);

        TextView eyebrow = text("LISTENER LAB / SAFE START", 11, 0xFFFFD166);
        eyebrow.setLetterSpacing(0.12f);
        page.addView(eyebrow);
        TextView title = text("STARTUP RECOVERY", 27, 0xFFECF7F3);
        title.setTypeface(null, 1);
        page.addView(title);
        TextView explanation = text("The previous run closed unexpectedly. Credentials and Home Assistant data are not present in this diagnostic.", 14, 0xFFAFC2C6);
        explanation.setPadding(0, dp(8), 0, dp(14));
        page.addView(explanation);
        TextView details = text(diagnostic, 13, 0xFFECF7F3);
        details.setTextIsSelectable(true);
        details.setPadding(dp(12), dp(12), dp(12), dp(12));
        details.setBackground(panelBackground(0xFF0A1A24, 0xFF35505B));
        page.addView(details, fullWidth(LinearLayout.LayoutParams.WRAP_CONTENT));

        Button copy = secondaryButton("COPY SAFE DIAGNOSTIC");
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Listener Lab startup diagnostic", diagnostic));
            Toast.makeText(this, "Safe diagnostic copied.", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyParams = fullWidth(dp(50));
        copyParams.topMargin = dp(12);
        page.addView(copy, copyParams);

        Button retry = primaryButton("RETRY IN SAFE START");
        retry.setOnClickListener(view -> {
            CrashReporter.clear(this);
            startNormalScreen();
        });
        LinearLayout.LayoutParams retryParams = fullWidth(dp(56));
        retryParams.topMargin = dp(8);
        page.addView(retry, retryParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private void connectAndAnalyze() {
        String manualToken = token.getText().toString().trim();
        if (manualPanel.getVisibility() == View.VISIBLE && !manualToken.isEmpty()) {
            startScanWithToken(manualToken);
            return;
        }
        setBusy(true, "CHECKING SECURE HOME ASSISTANT AUTHORIZATION");
        OAuthManager.getAccessToken(this, address.getText().toString(), new OAuthManager.Callback() {
            @Override public void onToken(String accessToken) {
                runOnUiThread(() -> startScanWithToken(accessToken));
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (OAuthManager.isNoCredential(message)) {
                        setBusy(false, "AUTHORIZATION REQUIRED / OPENING HOME ASSISTANT");
                        try {
                            OAuthManager.beginAuthorization(MainActivity.this, address.getText().toString());
                        } catch (Exception error) {
                            showFailure(error.getMessage());
                        }
                    } else showFailure(message);
                });
            }
        });
    }

    private void handleOAuthRedirect(Uri callback) {
        setBusy(true, "VERIFYING HOME ASSISTANT AUTHORIZATION");
        OAuthManager.handleRedirect(this, callback, new OAuthManager.Callback() {
            @Override public void onToken(String accessToken) {
                runOnUiThread(() -> {
                    String saved = OAuthManager.savedServer(MainActivity.this);
                    if (!saved.isEmpty()) address.setText(saved);
                    startScanWithToken(accessToken);
                });
            }

            @Override public void onFailure(String message) {
                runOnUiThread(() -> showFailure(message));
            }
        });
    }

    private void startScanWithToken(String accessToken) {
        setBusy(true, "READING DEVICES / ENTITIES / VOICE PIPELINES");
        latestReport = "";
        desktopButton.setVisibility(View.GONE);
        report.setText("SECURE SESSION ESTABLISHED\n\nBuilding the listener map…");
        new HomeAssistantScanner().scan(address.getText().toString(), accessToken,
                new HomeAssistantScanner.ResultCallback() {
                    @Override public void onSuccess(AnalysisResult value) {
                        runOnUiThread(() -> {
                            latestReport = value.report;
                            report.setText(value.report);
                            copyButton.setEnabled(true);
                            desktopButton.setVisibility(value.firmwareCandidate ? View.VISIBLE : View.GONE);
                            visualizer.showResult(value.confirmedListeners, value.possibleListeners);
                            setBusy(false, value.confirmedListeners > 0
                                    ? "ANALYSIS COMPLETE / LISTENERS IDENTIFIED"
                                    : "ANALYSIS COMPLETE / REVIEW POSSIBLE DEVICES");
                        });
                    }

                    @Override public void onFailure(String message) {
                        runOnUiThread(() -> showFailure(message));
                    }
                });
    }

    private void showFailure(String message) {
        report.setText("SCAN COULD NOT FINISH\n\n" + message + "\n\nNo settings were changed.");
        setBusy(false, "CONNECTION NEEDS ATTENTION");
    }

    private void setBusy(boolean busy, String message) {
        scanButton.setEnabled(!busy);
        address.setEnabled(!busy);
        token.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        visualizer.setScanning(busy);
        status.setText(message);
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Listener Lab report", latestReport));
        Toast.makeText(this, "Report copied — credentials are not included.", Toast.LENGTH_SHORT).show();
    }

    private void openHomeAssistant() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "Enter or discover the Home Assistant address first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (Exception error) {
            Toast.makeText(this, "No app could open that address.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDesktopGate() {
        new AlertDialog.Builder(this)
                .setTitle("Controlled escalation")
                .setMessage("Use the desktop path only after checking mute and placement, reviewing an Assist debug run, and trying the safe controls listed in this report. The computer tool will still require exact board and firmware-manifest verification.")
                .setNegativeButton("Not yet", null)
                .setPositiveButton("Open desktop tool", (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DESKTOP_URL))))
                .show();
    }

    private boolean isOAuthRedirect(Uri value) {
        return value != null && "listenerlab".equals(value.getScheme()) && "auth".equals(value.getHost());
    }

    private EditText input(String description, int inputType) {
        EditText field = new EditText(this);
        field.setContentDescription(description);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextColor(0xFFECF7F3);
        field.setHintTextColor(0xFF6F8993);
        field.setBackground(panelBackground(0xFF0A1A24, 0xFF35505B));
        field.setPadding(dp(14), 0, dp(14), 0);
        return field;
    }

    private Button primaryButton(String label) {
        return styledButton(label, 0xFF61D095, 0xFF07151F, 0xFF61D095);
    }

    private Button secondaryButton(String label) {
        return styledButton(label, 0xFF102734, 0xFFECF7F3, 0xFF35505B);
    }

    private Button warningButton(String label) {
        return styledButton(label, 0xFF3D321A, 0xFFFFD166, 0xFFFFD166);
    }

    private Button styledButton(String label, int background, int foreground, int stroke) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(13);
        value.setTextColor(foreground);
        value.setBackground(panelBackground(background, stroke));
        value.setAllCaps(false);
        value.setLetterSpacing(0.08f);
        return value;
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable value = new GradientDrawable();
        value.setShape(GradientDrawable.RECTANGLE);
        value.setColor(fill);
        value.setStroke(dp(1), stroke);
        value.setCornerRadius(dp(2));
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

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
