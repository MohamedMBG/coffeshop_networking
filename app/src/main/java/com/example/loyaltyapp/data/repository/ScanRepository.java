package com.example.loyaltyapp.data.repository;

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

public class ScanRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final long VISIT_TIME_WINDOW_MILLIS = 4 * 60 * 60 * 1000;

    public Task<Map<String, Object>> executeEarnTransaction(String voucherId, String userUid) {
        final DocumentReference voucherRef = db.collection("earn_codes").document(voucherId);
        final DocumentReference userRef = db.collection("users").document(userUid);
        final DocumentReference activityRef = userRef.collection("activities").document();

        return db.runTransaction(transaction -> {
            DocumentSnapshot voucherSnap = transaction.get(voucherRef);
            DocumentSnapshot userSnap = transaction.get(userRef);

            if (!voucherSnap.exists()) {
                throw new FirebaseFirestoreException("Voucher not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }

            String status = voucherSnap.getString("status");
            Long validForSec = voucherSnap.getLong("validForSec");
            Timestamp createdAt = voucherSnap.getTimestamp("createdAt");
            Long pointsLong = voucherSnap.getLong("points");
            int pointsVal = pointsLong != null ? pointsLong.intValue() : 0;

            if (status == null) {
                throw new FirebaseFirestoreException("Invalid voucher", FirebaseFirestoreException.Code.DATA_LOSS);
            }

            if (!"pending".equalsIgnoreCase(status)) {
                throw new FirebaseFirestoreException("Voucher is " + status, FirebaseFirestoreException.Code.ABORTED);
            }

            if (createdAt != null && validForSec != null) {
                long ageMs = System.currentTimeMillis() - createdAt.toDate().getTime();
                if (ageMs > validForSec * 1000L) {
                    throw new FirebaseFirestoreException("Voucher expired", FirebaseFirestoreException.Code.ABORTED);
                }
            }

            long currentPoints = userSnap.getLong("points") != null ? userSnap.getLong("points") : 0L;

            long currentVisits = userSnap.getLong("visits") != null ? userSnap.getLong("visits") : 0L;
            Timestamp lastVisitTs = userSnap.getTimestamp("lastVisitTimestamp");

            boolean incrementVisit = false;
            Date now = new Date();
            long nowMillis = now.getTime();

            if (lastVisitTs == null) {
                incrementVisit = true;
            } else {
                long lastVisitMillis = lastVisitTs.toDate().getTime();
                long timeDifference = nowMillis - lastVisitMillis;
                if (timeDifference > VISIT_TIME_WINDOW_MILLIS) {
                    incrementVisit = true;
                } else {
                    incrementVisit = false;
                }
            }

            Map<String, Object> vUpd = new HashMap<>();
            vUpd.put("status", "redeemed");
            vUpd.put("redeemedAt", new Timestamp(now));
            vUpd.put("redeemedByUid", userUid);
            transaction.update(voucherRef, vUpd);

            Map<String, Object> uUpd = new HashMap<>();
            uUpd.put("points", currentPoints + pointsVal);
            uUpd.put("updatedAt", new Timestamp(now));

            if (incrementVisit) {
                uUpd.put("visits", currentVisits + 1);
                uUpd.put("lastVisitTimestamp", new Timestamp(now));
            }

            transaction.update(userRef, uUpd);

            // P1: normalized activity log schema. See ActivityEvent javadoc.
            // Fields: type / delta / desc / refId / ts (status omitted for earn).
            Map<String, Object> log = new HashMap<>();
            log.put("type", "earn");
            log.put("delta", pointsVal);
            log.put("desc", "");
            log.put("refId", voucherId);
            log.put("ts", new Timestamp(now));
            transaction.set(activityRef, log);

            Map<String, Object> result = new HashMap<>();
            result.put("points", pointsVal);
            result.put("visitCounted", incrementVisit);
            return result;
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
