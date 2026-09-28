package com.listenerlab.app;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

final class HomeAssistantDiscovery {
    interface Callback {
        void onFound(String displayName, String url);
        void onFinishedWithoutResult();
    }

    private static final String SERVICE_TYPE = "_home-assistant._tcp.";
    private final NsdManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean resolving = new AtomicBoolean(false);
    private final Callback callback;
    private boolean stopped;

    HomeAssistantDiscovery(Context context, Callback callback) {
        this.manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.callback = callback;
    }

    void start() {
        if (manager == null) {
            callback.onFinishedWithoutResult();
            return;
        }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            handler.postDelayed(() -> {
                if (!resolving.get()) callback.onFinishedWithoutResult();
                stop();
            }, 7000);
        } catch (RuntimeException error) {
            callback.onFinishedWithoutResult();
        }
    }

    void stop() {
        if (stopped || manager == null) return;
        stopped = true;
        try {
            manager.stopServiceDiscovery(discoveryListener);
        } catch (RuntimeException ignored) {
            // Discovery may already have stopped after a platform error.
        }
    }

    private final NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
        @Override public void onDiscoveryStarted(String serviceType) {}

        @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
            if (!SERVICE_TYPE.equals(serviceInfo.getServiceType()) || !resolving.compareAndSet(false, true)) return;
            try {
                manager.resolveService(serviceInfo, resolveListener);
            } catch (RuntimeException error) {
                resolving.set(false);
            }
        }

        @Override public void onServiceLost(NsdServiceInfo serviceInfo) {}

        @Override public void onDiscoveryStopped(String serviceType) {}

        @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            callback.onFinishedWithoutResult();
            stop();
        }

        @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {}
    };

    @SuppressWarnings("deprecation")
    private final NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
        @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            resolving.set(false);
        }

        @Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
            InetAddress host = serviceInfo.getHost();
            if (host == null) {
                resolving.set(false);
                return;
            }
            String address = host.getHostAddress();
            if (address == null || address.isEmpty()) {
                resolving.set(false);
                return;
            }
            int zone = address.indexOf('%');
            if (zone >= 0) address = address.substring(0, zone);
            if (address.contains(":")) address = "[" + address + "]";
            String url = "http://" + address + ":" + serviceInfo.getPort();
            callback.onFound(serviceInfo.getServiceName(), url);
            stop();
        }
    };
}

