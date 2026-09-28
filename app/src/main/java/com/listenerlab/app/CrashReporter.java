package com.listenerlab.app;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class CrashReporter {
    private static final String FILE_NAME = "last_startup_crash.txt";

    private CrashReporter() {}

    static void install(Context context) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(context, error);
            if (previous != null) previous.uncaughtException(thread, error);
            else System.exit(10);
        });
    }

    static String read(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return "";
        StringBuilder value = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) value.append(line).append('\n');
        } catch (Exception ignored) {
            return "Previous Listener Lab startup failed; diagnostic details could not be read.";
        }
        return value.toString().trim();
    }

    static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.isFile()) file.delete();
    }

    private static void write(Context context, Throwable error) {
        StringBuilder value = new StringBuilder();
        value.append("Listener Lab ").append(versionName(context)).append('\n');
        value.append("Android SDK ").append(Build.VERSION.SDK_INT).append('\n');
        value.append("Failure: ").append(error.getClass().getName()).append('\n');
        int included = 0;
        for (StackTraceElement frame : error.getStackTrace()) {
            if (!frame.getClassName().startsWith("com.listenerlab.app")) continue;
            value.append("at ").append(frame.getClassName()).append('.')
                    .append(frame.getMethodName()).append(':').append(frame.getLineNumber()).append('\n');
            if (++included >= 12) break;
        }
        try (FileOutputStream output = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            output.write(value.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Never interfere with Android's normal crash handling.
        }
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown version";
        }
    }
}
