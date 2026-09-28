package com.listenerlab.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class HomeAssistantScanner {
    interface ResultCallback {
        void onSuccess(AnalysisResult result);
        void onFailure(String message);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build();

    void scan(String rawUrl, String token, ResultCallback callback) {
        final String baseUrl;
        try {
            baseUrl = normalizeUrl(rawUrl);
        } catch (IllegalArgumentException error) {
            callback.onFailure(error.getMessage());
            return;
        }
        if (token == null || token.trim().length() < 20) {
            callback.onFailure("Home Assistant access authorization is missing.");
            return;
        }

        new Thread(() -> {
            try {
                ScanData data = new ScanData();
                data.config = getObject(baseUrl + "/api/config", token.trim());
                data.states = getArray(baseUrl + "/api/states", token.trim());
                RegistryCollector collector = new RegistryCollector(client, baseUrl, token.trim(), data);
                collector.collect();
                callback.onSuccess(ListenerAnalyzer.analyze(data));
            } catch (Exception error) {
                callback.onFailure(friendlyError(error));
            }
        }, "listener-lab-scan").start();
    }

    private JSONObject getObject(String url, String token) throws IOException, JSONException {
        return new JSONObject(getBody(url, token));
    }

    private JSONArray getArray(String url, String token) throws IOException, JSONException {
        return new JSONArray(getBody(url, token));
    }

    private String getBody(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) {
                throw new IOException("Home Assistant rejected the token (401).");
            }
            if (!response.isSuccessful()) {
                throw new IOException("Home Assistant returned HTTP " + response.code() + ".");
            }
            if (response.body() == null) {
                throw new IOException("Home Assistant returned an empty response.");
            }
            return response.body().string();
        }
    }

    static String normalizeUrl(String rawUrl) {
        if (rawUrl == null) throw new IllegalArgumentException("Enter the Home Assistant address.");
        String value = rawUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.length() < 10) throw new IllegalArgumentException("Enter a valid Home Assistant address.");
        return value;
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        if (message.contains("CLEARTEXT")) {
            return "Android blocked the local HTTP connection. Try the exact HTTPS address used in a browser.";
        }
        if (message.contains("Failed to connect") || message.contains("timeout")) {
            return "Could not reach Home Assistant. Join the same network and check the address and port.";
        }
        return message;
    }

    private static final class RegistryCollector extends WebSocketListener {
        private final OkHttpClient client;
        private final String baseUrl;
        private final String token;
        private final ScanData data;
        private final CountDownLatch finished = new CountDownLatch(1);
        private final Set<Integer> replies = new HashSet<>();
        private final Map<Integer, String> satelliteRequestEntities = new HashMap<>();
        private volatile int expectedReplies = 3;
        private volatile boolean authenticated;
        private volatile String fatalMessage;
        private WebSocket socket;

        RegistryCollector(OkHttpClient client, String baseUrl, String token, ScanData data) {
            this.client = client;
            this.baseUrl = baseUrl;
            this.token = token;
            this.data = data;
        }

        void collect() throws InterruptedException {
            String socketUrl = baseUrl.replaceFirst("^http://", "ws://")
                    .replaceFirst("^https://", "wss://") + "/api/websocket";
            Request request = new Request.Builder().url(socketUrl).build();
            socket = client.newWebSocket(request, this);
            if (!finished.await(14, TimeUnit.SECONDS)) {
                data.warnings.put("Registry scan timed out; the REST inventory is still included.");
                socket.cancel();
            }
            if (fatalMessage != null && !authenticated) {
                data.warnings.put(fatalMessage);
            }
        }

        @Override public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                String type = message.optString("type");
                if ("auth_required".equals(type)) {
                    JSONObject auth = new JSONObject();
                    auth.put("type", "auth");
                    auth.put("access_token", token);
                    webSocket.send(auth.toString());
                    return;
                }
                if ("auth_invalid".equals(type)) {
                    fatalMessage = "WebSocket authentication failed.";
                    finished.countDown();
                    return;
                }
                if ("auth_ok".equals(type)) {
                    authenticated = true;
                    sendCommand(webSocket, 1, "config/device_registry/list", null);
                    sendCommand(webSocket, 2, "config/entity_registry/list", null);
                    sendCommand(webSocket, 3, "assist_pipeline/pipeline/list", null);
                    return;
                }
                if (!"result".equals(type)) return;
                int id = message.optInt("id", -1);
                if (id < 0 || replies.contains(id)) return;
                replies.add(id);
                boolean success = message.optBoolean("success", false);
                Object result = message.opt("result");
                if (success && id == 1 && result instanceof JSONArray) data.devices = (JSONArray) result;
                if (success && id == 2 && result instanceof JSONArray) {
                    data.entities = (JSONArray) result;
                    requestSatelliteConfigurations(webSocket, data.entities);
                }
                if (success && id == 3) data.pipelines = result;
                if (id >= 100 && success && result instanceof JSONObject) {
                    String entityId = satelliteRequestEntities.get(id);
                    if (entityId != null) data.satelliteConfigurations.put(entityId, (JSONObject) result);
                }
                if (!success && id <= 2) {
                    data.warnings.put("Home Assistant did not expose one registry command to this user.");
                }
                finishIfComplete(webSocket);
            } catch (JSONException error) {
                data.warnings.put("One Home Assistant response could not be read.");
            }
        }

        private void requestSatelliteConfigurations(WebSocket webSocket, JSONArray entities) throws JSONException {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.optJSONObject(i);
                if (entity == null) continue;
                String entityId = entity.optString("entity_id");
                if (entityId.startsWith("assist_satellite.")) ids.add(entityId);
            }
            expectedReplies = 3 + ids.size();
            int requestId = 100;
            for (String entityId : ids) {
                JSONObject fields = new JSONObject();
                fields.put("entity_id", entityId);
                satelliteRequestEntities.put(requestId, entityId);
                sendCommand(webSocket, requestId, "assist_satellite/get_configuration", fields);
                requestId++;
            }
        }

        private void sendCommand(WebSocket webSocket, int id, String type, JSONObject fields) throws JSONException {
            JSONObject command = fields == null ? new JSONObject() : fields;
            command.put("id", id);
            command.put("type", type);
            webSocket.send(command.toString());
        }

        private void finishIfComplete(WebSocket webSocket) {
            if (replies.size() >= expectedReplies) {
                webSocket.close(1000, "scan complete");
                finished.countDown();
            }
        }

        @Override public void onFailure(WebSocket webSocket, Throwable error, Response response) {
            fatalMessage = "Live registry connection failed; basic state discovery was used.";
            finished.countDown();
        }
    }
}
