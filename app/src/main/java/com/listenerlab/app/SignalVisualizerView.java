package com.listenerlab.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class SignalVisualizerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private float phase;
    private int confirmed;
    private int possible;
    private String mode = "STANDBY";

    SignalVisualizerView(Context context) {
        super(context);
        setContentDescription("Listener discovery visualizer: standby");
        grid.setColor(0x2423D18B);
        grid.setStrokeWidth(1f);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
    }

    void setScanning(boolean scanning) {
        mode = scanning ? "SCANNING" : mode;
        if (scanning && !animator.isStarted()) animator.start();
        if (!scanning && animator.isStarted()) animator.cancel();
        setContentDescription("Listener discovery visualizer: " + mode.toLowerCase());
        invalidate();
    }

    void showResult(int confirmed, int possible) {
        this.confirmed = confirmed;
        this.possible = possible;
        this.mode = confirmed > 0 ? "LISTENERS LOCKED" : possible > 0 ? "SIGNALS FOUND" : "NO LOCK";
        setScanning(false);
    }

    @Override protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        canvas.drawColor(0xFF0A1A24);
        for (int x = 0; x < width; x += Math.max(24, getWidth() / 12)) canvas.drawLine(x, 0, x, height, grid);
        for (int y = 0; y < height; y += Math.max(24, getHeight() / 6)) canvas.drawLine(0, y, width, y, grid);

        float cx = width * 0.5f;
        float cy = height * 0.48f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFF61D095);
        float pulse = 30f + phase * 58f;
        canvas.drawCircle(cx, cy, pulse, paint);
        paint.setAlpha(110);
        canvas.drawCircle(cx, cy, pulse + 24f, paint);
        paint.setAlpha(255);

        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 15f, paint);
        paint.setColor(0xFF07151F);
        canvas.drawRect(cx - 5f, cy - 9f, cx + 5f, cy + 6f, paint);

        int nodes = Math.max(3, confirmed + possible);
        for (int i = 0; i < nodes; i++) {
            double angle = (-Math.PI * 0.85) + (Math.PI * 1.7 * i / Math.max(1, nodes - 1));
            float radius = Math.min(width, height) * 0.39f;
            float nx = cx + (float) Math.cos(angle) * radius;
            float ny = cy + (float) Math.sin(angle) * radius;
            paint.setColor(i < confirmed ? 0xFF61D095 : i < confirmed + possible ? 0xFFFFD166 : 0xFF35505B);
            canvas.drawCircle(nx, ny, 7f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            canvas.drawLine(cx, cy, nx, ny, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        Path wave = new Path();
        float start = width * 0.1f;
        float end = width * 0.9f;
        for (int i = 0; i <= 80; i++) {
            float x = start + (end - start) * i / 80f;
            float amplitude = 8f + 10f * (float) Math.sin(phase * Math.PI * 2);
            float y = height * 0.82f + (float) Math.sin(i * 0.5f + phase * Math.PI * 4) * amplitude;
            if (i == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        paint.setColor(0xFF61D095);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawPath(wave, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(26f);
        paint.setColor(0xFFECF7F3);
        canvas.drawText(mode, 18f, 32f, paint);
        paint.setTextSize(20f);
        paint.setColor(0xFFAFC2C6);
        canvas.drawText("C " + confirmed + "  /  P " + possible, width - 130f, 32f, paint);
    }
}

