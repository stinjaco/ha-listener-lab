package com.listenerlab.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class OAuthManager {
    interface Callback {
        void onToken(String accessToken);
        void onFailure(String message);
    }

    static final String CLIENT_ID = "https://stinjaco.github.io/ha-listener-lab/";
    static final String REDIRECT_URI = "listenerlab://auth";
    private static final String PREFS = "listener_lab_auth";
    private static final String KEY_ALIAS = "listener_lab_oauth_key";
    private static final String NO_CREDENTIAL = "NO_CREDENTIAL";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private OAuthManager() {}

    static String savedServer(Context context) {
        try {
            TokenRecord record = TokenVault.load(context);
            return record == null ? "" : record.server;
        } catch (Exception ignored) {
            return "";
        }
    }

    static void beginAuthorization(Activity activity, String rawServer) {
        String server = HomeAssistantScanner.normalizeUrl(rawServer);
        String state = UUID.randomUUID().toString();
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("pending_state", state)
                .putString("pending_server", server)
                .apply();
        Uri authorize = Uri.parse(server + "/auth/authorize").buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("state", state)
                .build();
        activity.startActivity(new Intent(Intent.ACTION_VIEW, authorize));
    }

    static void handleRedirect(Context context, Uri uri, Callback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String expectedState = prefs.getString("pending_state", "");
        String server = prefs.getString("pending_server", "");
        String state = uri == null ? "" : uri.getQueryParameter("state");
        String code = uri == null ? "" : uri.getQueryParameter("code");
        String error = uri == null ? "" : uri.getQueryParameter("error");
        if (!error.isEmpty()) {
            callback.onFailure("Home Assistant authorization was canceled or rejected: " + error);
            return;
        }
        if (server.isEmpty() || code == null || code.isEmpty() || !expectedState.equals(state)) {
            callback.onFailure("The Home Assistant authorization response could not be verified.");
            return;
        }
        prefs.edit().remove("pending_state").remove("pending_server").apply();
        new Thread(() -> exchangeCode(context, server, code, callback), "listener-lab-oauth").start();
    }

    static void getAccessToken(Context context, String rawServer, Callback callback) {
        final String server;
        try {
            server = HomeAssistantScanner.normalizeUrl(rawServer);
        } catch (IllegalArgumentException error) {
            callback.onFailure(error.getMessage());
            return;
        }
        new Thread(() -> {
            try {
                TokenRecord record = TokenVault.load(context);
                if (record == null || !record.server.equals(server)) {
                    callback.onFailure(NO_CREDENTIAL);
                    return;
                }
                if (record.expiresAt > System.currentTimeMillis() + 60_000L) {
                    callback.onToken(record.accessToken);
                    return;
                }
                refresh(context, record, callback);
            } catch (Exception error) {
                callback.onFailure("Saved Home Assistant authorization could not be opened. Reconnect the app.");
            }
        }, "listener-lab-token").start();
    }

    static boolean isNoCredential(String message) {
        return NO_CREDENTIAL.equals(message);
    }

    static void forget(Context context) {
        TokenVault.clear(context);
    }

    private static void exchangeCode(Context context, String server, String code, Callback callback) {
        RequestBody form = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .build();
        requestToken(context, server, form, null, callback);
    }

    private static void refresh(Context context, TokenRecord record, Callback callback) {
        RequestBody form = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", record.refreshToken)
                .add("client_id", CLIENT_ID)
                .build();
        requestToken(context, record.server, form, record.refreshToken, callback);
    }

    private static void requestToken(Context context, String server, RequestBody form,
                                     String existingRefreshToken, Callback callback) {
        Request request = new Request.Builder().url(server + "/auth/token").post(form).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                callback.onFailure("Home Assistant authorization failed (HTTP " + response.code() + ").");
                return;
            }
            JSONObject value = new JSONObject(response.body().string());
            String accessToken = value.optString("access_token");
            String refreshToken = value.optString("refresh_token", existingRefreshToken == null ? "" : existingRefreshToken);
            long expiresAt = System.currentTimeMillis() + value.optLong("expires_in", 1800) * 1000L;
            if (accessToken.isEmpty() || refreshToken.isEmpty()) {
                callback.onFailure("Home Assistant returned incomplete authorization data.");
                return;
            }
            TokenVault.save(context, new TokenRecord(server, accessToken, refreshToken, expiresAt));
            callback.onToken(accessToken);
        } catch (Exception error) {
            callback.onFailure("Could not complete Home Assistant authorization: " + safeMessage(error));
        }
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class TokenRecord {
        final String server;
        final String accessToken;
        final String refreshToken;
        final long expiresAt;

        TokenRecord(String server, String accessToken, String refreshToken, long expiresAt) {
            this.server = server;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
    }

    private static final class TokenVault {
        private static SecretKey key() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        }

        static void save(Context context, TokenRecord record) throws Exception {
            JSONObject json = new JSONObject();
            json.put("server", record.server);
            json.put("access_token", record.accessToken);
            json.put("refresh_token", record.refreshToken);
            json.put("expires_at", record.expiresAt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("vault", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString("vault_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
        }

        static TokenRecord load(Context context) throws Exception {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String value = prefs.getString("vault", "");
            String iv = prefs.getString("vault_iv", "");
            if (value.isEmpty() || iv.isEmpty()) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(value, Base64.NO_WRAP));
            JSONObject json = new JSONObject(new String(plain, StandardCharsets.UTF_8));
            return new TokenRecord(json.getString("server"), json.getString("access_token"),
                    json.getString("refresh_token"), json.getLong("expires_at"));
        }

        static void clear(Context context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("vault").remove("vault_iv")
                    .remove("pending_state").remove("pending_server").apply();
        }
    }
}

