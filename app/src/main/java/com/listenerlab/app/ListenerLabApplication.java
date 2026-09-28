package com.listenerlab.app;

import android.app.Application;

public final class ListenerLabApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
