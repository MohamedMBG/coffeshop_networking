package com.example.loyaltyapp.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;

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

    /**
     * Upsert this device on the backend whenever we have an FCM token.
     *
     * @param ctx any Context; only the application context is retained (via
     *            {@link Context#getApplicationContext()}), so passing a
     *            short-lived Activity/Service context is leak-safe.
     */
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
                        // Log the cause for diagnosis. The FCM token lives in the
                        // request body, not the throwable, so this stays P0-safe.
                        Log.e(TAG, "registerDevice network error", t);
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

    /**
     * Disable this install on the backend before logout, then invalidate the local FCM token.
     * The completion always runs so logout is not blocked by an outage; deleting the FCM token
     * still prevents the stale server token from delivering, and a later campaign marks it dead.
     *
     * @param ctx application or activity context.
     * @param completion action that signs the user out and navigates away.
     */
    public static void unregisterForLogout(Context ctx, Runnable completion) {
        Context app = ctx.getApplicationContext();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            deleteTokenAndFinish(completion);
            return;
        }
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.unregisterDevice(new ApiService.DeviceIdRequest(deviceId(app)))
                .enqueue(new Callback<ApiResponse<ApiService.UnregisterDeviceResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.UnregisterDeviceResult>> call,
                                           Response<ApiResponse<ApiService.UnregisterDeviceResult>> response) {
                        Log.i(TAG, "unregisterDevice code=" + response.code());
                        deleteTokenAndFinish(completion);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.UnregisterDeviceResult>> call,
                                          Throwable error) {
                        Log.w(TAG, "unregisterDevice unavailable; invalidating local token", error);
                        deleteTokenAndFinish(completion);
                    }
                });
    }

    private static void deleteTokenAndFinish(Runnable completion) {
        FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener(task -> completion.run());
    }
}
