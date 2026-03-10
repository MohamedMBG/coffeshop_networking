package com.example.loyaltyapp.data.repository;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.loyaltyapp.models.Rewards;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RewardsRepository {
    private static final String TAG = "RewardsRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final String COL_REWARDS = "rewards_catalog";
    private static final String COL_USERS = "users";
    private static final String COL_ACTIVITIES = "activities";

    // Firestore Fields
    private static final String F_ACTIVE = "active";
    private static final String F_CATEGORY = "category";
    private static final String F_POINTS = "redeemPoints";
    private static final String F_NAME = "name";
    private static final String F_IMAGE = "imagePath";
    private static final String F_DESC = "description";
    private static final String F_TERMS = "termsUrl";
    private static final String F_EXP_DAYS = "expirationDays";

    public interface OnRewardsLoaded {
        void onSuccess(List<Rewards> rewards);
        void onError(Exception e);
    }

    public interface RedeemCallback {
        void onSuccess();
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
        r.imagePath = d.getString(F_IMAGE) != null ? d.getString(F_IMAGE) : "";
        r.category = d.getString(F_CATEGORY) != null ? d.getString(F_CATEGORY) : "";
        long p = d.getLong(F_POINTS) != null ? d.getLong(F_POINTS) : 0;
        r.redeemPoints = (int) p;
        long e = d.getLong(F_EXP_DAYS) != null ? d.getLong(F_EXP_DAYS) : 30;
        return r;
    }

    public void submitRedemption(Rewards reward, RedeemCallback callback) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            callback.onError("User not authenticated");
            return;
        }
        String uid = u.getUid();
        
        DocumentReference userRef = db.collection(COL_USERS).document(uid);
        DocumentReference logRef = userRef.collection(COL_ACTIVITIES).document();
        
        db.runTransaction(transaction -> {
            DocumentSnapshot userSnap = transaction.get(userRef);
            if (!userSnap.exists()) {
                throw new Exception("User not found");
            }

            Long currentPointsLong = userSnap.getLong("points");
            int currentPoints = currentPointsLong != null ? currentPointsLong.intValue() : 0;
            
            if (currentPoints < reward.redeemPoints) {
                throw new Exception("Insufficient points");
            }

            // Deduct points
            transaction.update(userRef, "points", currentPoints - reward.redeemPoints);

            // Create activity log
            transaction.set(logRef, new RedemptionLog(
                    "redemption",
                    Timestamp.now(),
                    -reward.redeemPoints,
                    reward.name,
                    reward.id,
                    "completed"
            ));

            return null;
        }).addOnSuccessListener(ignored -> {
            callback.onSuccess();
        }).addOnFailureListener(err -> {
            callback.onError(err.getMessage());
        });
    }

    // Helper model for transaction logging
    public static class RedemptionLog {
        public String type;
        public Timestamp ts;
        public int delta;
        public String desc;
        public String rewardId;
        public String status;

        public RedemptionLog() {}
        public RedemptionLog(String type, Timestamp ts, int delta, String desc, String rewardId, String status) {
            this.type = type;
            this.ts = ts;
            this.delta = delta;
            this.desc = desc;
            this.rewardId = rewardId;
            this.status = status;
        }
    }
}
