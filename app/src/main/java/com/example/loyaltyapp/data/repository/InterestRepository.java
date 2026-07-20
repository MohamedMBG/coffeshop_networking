package com.example.loyaltyapp.data.repository;

import android.os.SystemClock;
import android.util.Log;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Sends authenticated menu-category behavior to the backend for push segmentation.
 * <p>
 * Repeated taps on the same category are suppressed for 15 seconds to avoid inflating a preference
 * from UI double-taps or RecyclerView rebinding. The backend validates and rate-limits every event
 * and updates only the authenticated user's aggregate.
 */
public class InterestRepository {

    private static final String TAG = "InterestRepository";
    private static final long SAME_CATEGORY_DEBOUNCE_MS = 15_000L;
    private final ApiService api;
    private final Map<String, Long> lastSentAt = new HashMap<>();

    public InterestRepository() {
        this(ApiClient.getClient().create(ApiService.class));
    }

    InterestRepository(ApiService api) {
        this.api = api;
    }

    /**
     * Record a category selection asynchronously. Failures are non-blocking because menu browsing
     * must keep working when analytics delivery is offline.
     */
    public void record(String rawCategory) {
        if (rawCategory == null || rawCategory.trim().isEmpty()) return;
        String category = rawCategory.trim().toLowerCase(Locale.ROOT);
        long now = SystemClock.elapsedRealtime();
        synchronized (lastSentAt) {
            Long previous = lastSentAt.get(category);
            if (previous != null && now - previous < SAME_CATEGORY_DEBOUNCE_MS) return;
            lastSentAt.put(category, now);
        }
        api.recordInterest(new ApiService.InterestRequest(category))
                .enqueue(new Callback<ApiResponse<ApiService.InterestResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.InterestResult>> call,
                                           Response<ApiResponse<ApiService.InterestResult>> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "interest event rejected: HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.InterestResult>> call, Throwable error) {
                        Log.w(TAG, "interest event unavailable", error);
                    }
                });
    }
}
