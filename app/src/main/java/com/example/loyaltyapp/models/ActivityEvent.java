package com.example.loyaltyapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Unified shape for a user's activity history entry. One schema for every
 * `users/{uid}/activities/{doc}` document, regardless of source repository.
 *
 * Canonical fields (write these):
 *   type    "earn" | "spend" | "redemption" | "bonus"
 *   delta   signed points change (+ for earn/bonus, - for spend/redemption)
 *   desc    short label for the UI (item name, reward name, "")
 *   refId   server-issued ID this activity references
 *           (voucherId / redeemCodeId / rewardId)
 *   status  optional lifecycle marker (e.g. "completed" for redemption)
 *   ts      server timestamp
 *
 * fromDoc() also reads legacy fields written before this normalization
 * (`points`, `item`, `storeName`, `voucherId`, `redeemCodeId`, `rewardId`)
 * so existing documents continue to display correctly.
 */
public class ActivityEvent {
    public String id;
    public String type;
    public int delta;
    public String desc;
    public String refId;
    public String status;
    public Timestamp ts;

    public static final String TYPE_EARN = "earn";
    public static final String TYPE_SPEND = "spend";
    public static final String TYPE_REDEMPTION = "redemption";
    public static final String TYPE_BONUS = "bonus";

    public static ActivityEvent fromDoc(DocumentSnapshot d) {
        try {
            ActivityEvent e = new ActivityEvent();
            e.id = d.getId();
            e.type = normalizeType(safeStr(d.getString("type")));

            // Prefer normalized `delta`, fall back to legacy `points`.
            Number deltaN = d.getLong("delta");
            if (deltaN == null) deltaN = d.getLong("points");
            e.delta = deltaN == null ? 0 : deltaN.intValue();

            // Prefer normalized `desc`, fall back to legacy alternatives.
            String desc = d.getString("desc");
            if (desc == null) desc = d.getString("item");
            if (desc == null) desc = d.getString("storeName");
            e.desc = safeStr(desc);

            // Prefer normalized `refId`, fall back to per-type legacy IDs.
            String refId = d.getString("refId");
            if (refId == null) refId = d.getString("voucherId");
            if (refId == null) refId = d.getString("redeemCodeId");
            if (refId == null) refId = d.getString("rewardId");
            e.refId = safeStr(refId);

            e.status = safeStr(d.getString("status"));
            e.ts = d.getTimestamp("ts");
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    // Legacy aliases written by old code paths.
    private static String normalizeType(String raw) {
        if (raw == null) return "";
        switch (raw.toLowerCase()) {
            case "scan":   return TYPE_EARN;
            case "redeem": return TYPE_REDEMPTION;
            default:       return raw;
        }
    }

    private static String safeStr(String s) { return s == null ? "" : s; }
}
