package com.example.loyaltyapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Unified shape for a user's activity history entry. One schema for every
 * `users/{uid}/activities/{doc}` document.
 *
 * Backend-written fields (source of truth after migration):
 *   type          "earn" | "redeem" | "cancel" | "expire" | "birthday" | "adjust"
 *   pointsDelta   signed points change (read into {@link #delta})
 *   createdAt     server timestamp (read into {@link #ts})
 *   refId         server-issued ID this activity references
 *   balanceAfter  points balance after this entry
 *
 * fromDoc() also reads legacy fields written by the pre-backend app
 * (`delta`, `points`, `ts`, `item`, `storeName`, `voucherId`, ...) and maps
 * legacy type values to the canonical set, so old documents still display.
 */
public class ActivityEvent {
    public String id;
    public String type;
    public int delta;
    public String desc;
    public String refId;
    public String status;
    public Timestamp ts;
    public int balanceAfter;

    public static final String TYPE_EARN = "earn";
    public static final String TYPE_REDEEM = "redeem";
    public static final String TYPE_CANCEL = "cancel";
    public static final String TYPE_EXPIRE = "expire";
    public static final String TYPE_BIRTHDAY = "birthday";
    public static final String TYPE_ADJUST = "adjust";

    public static ActivityEvent fromDoc(DocumentSnapshot d) {
        try {
            ActivityEvent e = new ActivityEvent();
            e.id = d.getId();
            e.type = normalizeType(safeStr(d.getString("type")));

            // Prefer backend `pointsDelta`, fall back to legacy `delta`/`points`.
            Number deltaN = d.getLong("pointsDelta");
            if (deltaN == null) deltaN = d.getLong("delta");
            if (deltaN == null) deltaN = d.getLong("points");
            e.delta = deltaN == null ? 0 : deltaN.intValue();

            // Prefer normalized `desc`, fall back to legacy alternatives.
            String desc = d.getString("desc");
            if (desc == null) desc = d.getString("item");
            if (desc == null) desc = d.getString("storeName");
            e.desc = safeStr(desc);

            // Prefer backend `refId`, fall back to per-type legacy IDs.
            String refId = d.getString("refId");
            if (refId == null) refId = d.getString("voucherId");
            if (refId == null) refId = d.getString("redeemCodeId");
            if (refId == null) refId = d.getString("rewardId");
            e.refId = safeStr(refId);

            e.status = safeStr(d.getString("status"));

            // Prefer backend `createdAt`, fall back to legacy `ts`.
            Timestamp ts = readTs(d, "createdAt");
            if (ts == null) ts = readTs(d, "ts");
            e.ts = ts;

            Number bal = d.getLong("balanceAfter");
            e.balanceAfter = bal == null ? 0 : bal.intValue();
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    // Map legacy old-app type values onto the canonical backend set.
    private static String normalizeType(String raw) {
        if (raw == null) return "";
        switch (raw.toLowerCase()) {
            case "scan":       return TYPE_EARN;
            case "spend":      return TYPE_REDEEM;
            case "redemption": return TYPE_REDEEM;
            case "bonus":      return TYPE_ADJUST;
            default:           return raw.toLowerCase();
        }
    }

    /** Reads a timestamp field that may be a Firestore Timestamp or epoch millis. */
    private static Timestamp readTs(DocumentSnapshot d, String field) {
        Object v = d.get(field);
        if (v instanceof Timestamp) return (Timestamp) v;
        if (v instanceof Number) {
            long ms = ((Number) v).longValue();
            return new Timestamp(ms / 1000, (int) ((ms % 1000) * 1_000_000));
        }
        return null;
    }

    private static String safeStr(String s) { return s == null ? "" : s; }
}
