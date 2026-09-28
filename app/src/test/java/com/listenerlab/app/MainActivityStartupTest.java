package com.listenerlab.app;

import android.content.Context;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityStartupTest {
    @Test public void coldStartBuildsInteractiveScreen() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.create().start().resume().visible().get();

            assertNotNull(activity);
            Button connect = findButton(activity.findViewById(android.R.id.content), "CONNECT + ANALYZE");
            assertNotNull(connect);
            assertTrue(connect.isEnabled());
        }
    }

    @Test public void previousCrashOpensSafeRecoveryScreen() throws Exception {
        Context context = org.robolectric.RuntimeEnvironment.getApplication();
        try (java.io.FileOutputStream output = context.openFileOutput(
                "last_startup_crash.txt", Context.MODE_PRIVATE)) {
            output.write("Listener Lab test\nFailure: test.StartupFailure".getBytes(StandardCharsets.UTF_8));
        }

        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class)) {
            MainActivity activity = controller.create().start().resume().visible().get();

            assertNotNull(findButton(activity.findViewById(android.R.id.content), "COPY SAFE DIAGNOSTIC"));
            assertNotNull(findButton(activity.findViewById(android.R.id.content), "RETRY IN SAFE START"));
        } finally {
            CrashReporter.clear(context);
        }
    }

    private Button findButton(android.view.View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
