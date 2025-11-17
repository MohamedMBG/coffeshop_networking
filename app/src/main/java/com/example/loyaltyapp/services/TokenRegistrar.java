package com.example.loyaltyapp.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class TokenRegistrar {
    private static final String TAG = "TokenRegistrar";
    // Your backend base URL
    private static final String API_BASE = "https://email-api-git-main-programmingmbmy-3449s-projects.vercel.app";

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    // Reuse one OkHttpClient with sane timeouts
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build();

    private TokenRegistrar() {}

    // -------------- PUBLIC APIS --------------

    /** Idempotent: upserts the device into backend "devices" whenever you have an FCM token. */
    public static void ensureDevice(String token, @Nullable String role) {
        try {
            JSONObject body = defaultBody(token, role);
            String url = API_BASE + "/api/push/registerDevice";
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null) {
                // Get Firebase ID token, then call backend asynchronously
                u.getIdToken(true).addOnSuccessListener(res -> {
                    String bearer = res.getToken();
                    upsertDevice(url, body, bearer);
                }).addOnFailureListener(e -> {
                    Log.w(TAG, "getIdToken failed, sending without bearer: " + e.getMessage());
                    upsertDevice(url, body, null);
                });
            } else {
                upsertDevice(url, body, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "ensureDevice build body failed", e);
        }
    }

    /** Same as ensureDevice; kept for compatibility with your existing calls. */
    public static void sendTokenToServer(Context ctx, String token, @Nullable String role) {
        ensureDevice(token, role);
    }

    // -------------- INTERNAL HELPERS --------------

    private static JSONObject defaultBody(String token, @Nullable String role) throws Exception {
        JSONObject body = new JSONObject();
        body.put("token", token);
        body.put("platform", "android");
        body.put("role", (role == null || role.isEmpty()) ? "client" : role);
        body.put("appVersion", "1.0.0");
        body.put("points", 0); // harmless; your backend can ignore/merge
        body.put("topics", new JSONArray(Collections.singletonList("general")));
        return body;
    }

    /** Always async. Never blocks UI. Adds Authorization if provided. */
    private static void upsertDevice(String url, JSONObject body, @Nullable String bearer) {
        try {
            RequestBody reqBody = RequestBody.create(body.toString(), JSON);
            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .addHeader("Content-Type", "application/json");

            if (bearer != null && !bearer.isEmpty()) {
                rb.addHeader("Authorization", "Bearer " + bearer);
            }

            Request req = rb.build();
            Log.i(TAG, "POST " + url + " body=" + body.toString());

            HTTP.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, java.io.IOException e) {
                    Log.e(TAG, "registerDevice network error", e);
                }

                @Override public void onResponse(Call call, Response response) {
                    try {
                        String respStr = response.body() != null ? response.body().string() : "";
                        Log.i(TAG, "registerDevice response code=" + response.code() + " body=" + respStr);
                    } catch (Exception e) {
                        Log.w(TAG, "registerDevice read body failed", e);
                    } finally {
                        if (response.body() != null) response.body().close();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "upsertDevice build request failed", e);
        }
    }
}
