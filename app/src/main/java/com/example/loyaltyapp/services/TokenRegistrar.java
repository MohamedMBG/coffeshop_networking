package com.example.loyaltyapp.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;
import com.google.firebase.auth.FirebaseAuth;

import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Registers this install's FCM token with the backend
 * (POST /api/v1/push/registerDevice, body {deviceId, fcmToken, platform}).
 * The auth interceptor attaches the Firebase bearer token. deviceId is a stable
 * per-install id kept in app-private prefs. The FCM token is NEVER logged.
 */
public final class TokenRegistrar {
    private static final String TAG = "TokenRegistrar";
    private static final String PREFS = "device_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String PLATFORM = "android";

    private TokenRegistrar() {}

    /** Upsert this device on the backend whenever we have an FCM token. */
    public static void ensureDevice(Context ctx, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) return;
        // No bearer when signed out -> backend would 401. Skip proving nothing.
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.w(TAG, "skip registerDevice: signed out");
            return;
        }

        String deviceId = deviceId(ctx.getApplicationContext());
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.registerDevice(new ApiService.DeviceRequest(deviceId, fcmToken, PLATFORM))
                .enqueue(new Callback<ApiResponse<ApiService.DeviceResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.DeviceResult>> call,
                                           Response<ApiResponse<ApiService.DeviceResult>> resp) {
                        // Never log the token or body; status code only.
                        Log.i(TAG, "registerDevice code=" + resp.code());
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.DeviceResult>> call, Throwable t) {
                        Log.e(TAG, "registerDevice network error");
                    }
                });
    }

    /**
     * Stable per-install id: generated once and persisted in app-private prefs.
     * Dashes stripped so it stays within the backend's [A-Za-z0-9_-] doc-id rule.
     */
    private static String deviceId(Context appCtx) {
        SharedPreferences p = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = p.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
            p.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }
}
