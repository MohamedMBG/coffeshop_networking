package com.example.loyaltyapp.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiErrors;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;
import com.example.loyaltyapp.Idempotency;
import com.example.loyaltyapp.models.Rewards;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class RewardsRepository {
    private static final String TAG = "RewardsRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Injected once so the Retrofit proxy isn't rebuilt per call and can be
    // swapped in tests.
    private final ApiService api;

    public RewardsRepository() {
        this(ApiClient.getClient().create(ApiService.class));
    }

    @VisibleForTesting
    public RewardsRepository(ApiService api) {
        this.api = api;
    }

    private static final String COL_REWARDS = "rewards_catalog";

    // Firestore Fields
    private static final String F_ACTIVE = "active";
    private static final String F_CATEGORY = "category";
    private static final String F_POINTS = "cost";
    private static final String F_NAME = "name";
    private static final String F_IMAGE = "imageUrl";
    private static final String F_EXP_DAYS = "expirationDays";

    public interface OnRewardsLoaded {
        void onSuccess(List<Rewards> rewards);
        void onError(Exception e);
    }

    public interface RedeemCallback {
        /**
         * @param code             pending redeem code to show as a QR for the cashier
         * @param expiresAtEpochMs when the pending code expires (drives the countdown)
         * @param totalPoints      remaining balance after the deduction
         */
        void onSuccess(String code, long expiresAtEpochMs, int totalPoints);
        void onError(String message);
    }

    public void fetchRewards(String categoryFilter, OnRewardsLoaded callback) {
        Query q = db.collection(COL_REWARDS).whereEqualTo(F_ACTIVE, true);
        if (!"all".equalsIgnoreCase(categoryFilter)) {
            q = q.whereEqualTo(F_CATEGORY, categoryFilter);
        }

        // Try ordering on the server first (requires composite index)
        Query orderedQuery = q.orderBy(F_POINTS, Query.Direction.ASCENDING);

        orderedQuery.get(Source.SERVER)
                .addOnSuccessListener(snap -> callback.onSuccess(parseSnap(snap, false)))
                .addOnFailureListener(err -> {
                    // Fallback to client-side sorting if index doesn't exist
                    if (err instanceof FirebaseFirestoreException &&
                            ((FirebaseFirestoreException) err).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Log.w(TAG, "Missing index, sorting on client.", err);

                        // Query without orderBy
                        Query fallbackQuery = db.collection(COL_REWARDS).whereEqualTo(F_ACTIVE, true);
                        if (!"all".equalsIgnoreCase(categoryFilter)) {
                            fallbackQuery = fallbackQuery.whereEqualTo(F_CATEGORY, categoryFilter);
                        }

                        fallbackQuery.get(Source.SERVER)
                                .addOnSuccessListener(snap -> callback.onSuccess(parseSnap(snap, true)))
                                .addOnFailureListener(callback::onError);
                    } else {
                        callback.onError(err);
                    }
                });
    }

    private List<Rewards> parseSnap(QuerySnapshot snap, boolean sortOnDevice) {
        List<Rewards> list = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            Rewards r = parseReward(d);
            if (r != null) {
                list.add(r);
            }
        }
        if (sortOnDevice) {
            Collections.sort(list, Comparator.comparingInt(o -> o.redeemPoints));
        }
        return list;
    }

    @Nullable
    private Rewards parseReward(@NonNull DocumentSnapshot d) {
        Boolean active = d.getBoolean(F_ACTIVE);
        String name = d.getString(F_NAME);
        if (active == null || !active || name == null) return null;

        Rewards r = new Rewards();
        r.id = d.getId();
        r.name = name;
        // Prefer backend field, fall back to the legacy name so pre-migration
        // catalog documents still parse.
        String img = d.getString(F_IMAGE);
        if (img == null) img = d.getString("imagePath");
        r.imagePath = img != null ? img : "";
        r.category = d.getString(F_CATEGORY) != null ? d.getString(F_CATEGORY) : "";
        Long pts = d.getLong(F_POINTS);
        if (pts == null) pts = d.getLong("redeemPoints");
        r.redeemPoints = pts != null ? pts.intValue() : 0;
        return r;
    }

    /**
     * Redeem a reward through the backend (POST /rewards/redeem). The backend
     * deducts the points and returns a pending code the customer shows to the
     * cashier; it does NOT complete the spend. Deducting points on the client is
     * blocked by firestore.rules, so this replaces the old in-app transaction.
     */
    public void redeem(String rewardId, RedeemCallback cb) {
        // One idempotency key per redeem tap; a retry replays instead of
        // charging twice or creating a second pending code.
        api.redeem(Idempotency.newKey(), new ApiService.RedeemRequest(rewardId))
                .enqueue(new retrofit2.Callback<ApiResponse<ApiService.RedeemResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.RedeemResult>> call,
                                           Response<ApiResponse<ApiService.RedeemResult>> resp) {
                        ApiResponse<ApiService.RedeemResult> body = resp.body();
                        if (resp.isSuccessful() && body != null && body.ok && body.data != null) {
                            ApiService.RedeemResult d = body.data;
                            cb.onSuccess(d.code, d.expiresAtEpochMs, d.totalPoints);
                        } else {
                            cb.onError(ApiErrors.messageFor(resp));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.RedeemResult>> call, Throwable throwable) {
                        Log.e(TAG, "redeem transport failure", throwable);
                        cb.onError(ApiErrors.networkMessageFor(throwable));
                    }
                });
    }

    public interface CancelCallback {
        void onSuccess(int refunded, int totalPoints);
        void onError(String message);
    }

    /**
     * Cancel a pending redeem (POST /rewards/redeem/cancel). The backend refunds
     * the points and clears the pending code — used to escape the one-pending
     * limit or when the customer changes their mind.
     */
    public void cancelRedeem(String code, CancelCallback cb) {
        api.cancelRedeem(Idempotency.newKey(), new ApiService.CancelRequest(code))
                .enqueue(new retrofit2.Callback<ApiResponse<ApiService.CancelResult>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ApiService.CancelResult>> call,
                                           Response<ApiResponse<ApiService.CancelResult>> resp) {
                        ApiResponse<ApiService.CancelResult> body = resp.body();
                        if (resp.isSuccessful() && body != null && body.ok && body.data != null) {
                            cb.onSuccess(body.data.refunded, body.data.totalPoints);
                        } else {
                            cb.onError(ApiErrors.messageFor(resp));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.CancelResult>> call, Throwable throwable) {
                        Log.e(TAG, "cancel transport failure", throwable);
                        cb.onError(ApiErrors.networkMessageFor(throwable));
                    }
                });
    }
}
