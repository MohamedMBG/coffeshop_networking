package com.example.loyaltyapp.data.repository;

import com.example.loyaltyapp.ApiClient;
import com.example.loyaltyapp.ApiErrors;
import com.example.loyaltyapp.ApiResponse;
import com.example.loyaltyapp.ApiService;
import com.example.loyaltyapp.Idempotency;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;

public class ScanRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final long VISIT_TIME_WINDOW_MILLIS = 4 * 60 * 60 * 1000;

    public interface EarnCallback{
        void onSuccess(int pointsGranted, int totalPoints, int totalVisits);
        void onError(String messgage);
    }

    public void earn(String code, EarnCallback cb){
        ApiService api = ApiClient.getClient().create(ApiService.class);
        //One idempotency key per scan; the auth interceptor's 401-retry replays
        // the same key, s a retry never double-earns
        api.earn(Idempotency.newKey() , new ApiService.EarnRequest(code))
                .enqueue(new retrofit2.Callback<ApiResponse<ApiService.EarnResult>>() {
                    @Override
                    public void onResponse(retrofit2.Call<ApiResponse<ApiService.EarnResult>> call ,
                                           retrofit2.Response<ApiResponse<ApiService.EarnResult>> resp){
                        ApiResponse<ApiService.EarnResult> body = resp.body();
                        if (resp.isSuccessful() && body != null && body.ok && body.data != null) {
                            ApiService.EarnResult result = body.data;
                            cb.onSuccess(result.pointsGranted , result.totalPoints , result.totalVisits);
                        } else {
                            cb.onError(ApiErrors.messageFor(resp));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ApiService.EarnResult>> call, Throwable throwable) {
                        cb.onError(throwable.getMessage());
                    }
                });
    }

    public Task<String> executeSpendTransaction(String redeemDocId, String qrUserUid, int qrCostPoints, String currentUserUid) {
        final DocumentReference redeemRef = db.collection("redeem_codes").document(redeemDocId);
        final DocumentReference userRef = db.collection("users").document(currentUserUid);
        final DocumentReference activityRef = userRef.collection("activities").document();

        return db.runTransaction(transaction -> {
            DocumentSnapshot redeemSnap = transaction.get(redeemRef);
            DocumentSnapshot userSnap = transaction.get(userRef);

            if (!redeemSnap.exists()) {
                throw new FirebaseFirestoreException("Redemption code not found",
                        FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String targetUid = redeemSnap.getString("userUid");
            if (targetUid != null && !targetUid.equals(currentUserUid)) {
                throw new FirebaseFirestoreException("Code belongs to another user",
                        FirebaseFirestoreException.Code.PERMISSION_DENIED);
            }

            String status = redeemSnap.getString("status");
            String type = redeemSnap.getString("type");
            Long costLong = redeemSnap.getLong("costPoints");
            String itemName = redeemSnap.getString("itemName");
            int costPoints = costLong != null ? costLong.intValue() : qrCostPoints;

            if (type == null || !"REDEEM".equalsIgnoreCase(type)) {
                throw new FirebaseFirestoreException("Wrong code type",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            if (status == null) {
                throw new FirebaseFirestoreException("Invalid code status",
                        FirebaseFirestoreException.Code.DATA_LOSS);
            }

            if (!"ACTIVE".equalsIgnoreCase(status)) {
                throw new FirebaseFirestoreException("Code already " + status,
                        FirebaseFirestoreException.Code.ABORTED);
            }

            long userBalance = userSnap.getLong("points") != null ? userSnap.getLong("points") : 0L;

            if (userBalance < costPoints) {
                throw new FirebaseFirestoreException("Insufficient Points",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            Map<String, Object> uUpd = new HashMap<>();
            uUpd.put("points", userBalance - costPoints);
            uUpd.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(userRef, uUpd);

            Map<String, Object> rUpd = new HashMap<>();
            rUpd.put("status", "completed");
            rUpd.put("completedAt", FieldValue.serverTimestamp());
            rUpd.put("completedByUid", currentUserUid);
            transaction.update(redeemRef, rUpd);

            // P1: normalized activity log schema. See ActivityEvent javadoc.
            Map<String, Object> log = new HashMap<>();
            log.put("type", "spend");
            log.put("delta", -costPoints);
            log.put("desc", itemName != null ? itemName : "");
            log.put("refId", redeemDocId);
            log.put("status", "completed");
            log.put("ts", FieldValue.serverTimestamp());
            transaction.set(activityRef, log);

            return itemName != null ? itemName : "Reward";
        });
    }
}
