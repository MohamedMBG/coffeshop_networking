package com.example.loyaltyapp.data.repository;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiErrors;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;
import com.example.loyaltyapp.Idempotency;

import retrofit2.Call;
import retrofit2.Response;

public class ScanRepository {
    private static final String TAG = "ScanRepository";

    // Injected once so the Retrofit proxy isn't rebuilt per call and can be
    // swapped in tests.
    private final ApiService api;

    public ScanRepository() {
        this(ApiClient.getClient().create(ApiService.class));
    }

    @VisibleForTesting
    public ScanRepository(ApiService api) {
        this.api = api;
    }

    public interface EarnCallback {
        void onSuccess(int pointsGranted, int totalPoints, int totalVisits);
        void onError(String message);
    }

    /**
     * Redeem a scanned earn code through the backend (POST /loyalty/earn).
     * The auth interceptor attaches the Firebase token, so no uid is passed.
     * Success/error are reported on the main thread via {@code cb}.
     */
    public void earn(String code, EarnCallback cb) {
        // One idempotency key per scan; the auth interceptor's 401-retry replays
        // the same key, so a retry never double-earns.
        api.earn(Idempotency.newKey(), new ApiService.EarnRequest(code))
                .enqueue(new retrofit2.Callback<ApiResponse<ApiService.EarnResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.EarnResult>> call,
                                           Response<ApiResponse<ApiService.EarnResult>> resp) {
                        ApiResponse<ApiService.EarnResult> body = resp.body();
                        if (resp.isSuccessful() && body != null && body.ok && body.data != null) {
                            ApiService.EarnResult result = body.data;
                            cb.onSuccess(result.pointsGranted, result.totalPoints, result.totalVisits);
                        } else {
                            cb.onError(ApiErrors.messageFor(resp));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.EarnResult>> call, Throwable throwable) {
                        // Transport-level failure (no HTTP response). Log the cause
                        // for debugging; the user-facing copy lives in ApiErrors.
                        Log.e(TAG, "earn transport failure", throwable);
                        cb.onError(ApiErrors.networkMessageFor(throwable));
                    }
                });
    }
}
