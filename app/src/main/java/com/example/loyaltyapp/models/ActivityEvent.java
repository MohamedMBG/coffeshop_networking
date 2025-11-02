package com.example.loyaltyapp.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public class ActivityEvent {
    public String id;
    public String type;       // "scan" | "redemption" | "bonus"
    public int points;        // delta (+/-)
    public String storeName;  // optional
    public Timestamp ts;

    public static ActivityEvent fromDoc(DocumentSnapshot d) {
        try {
            ActivityEvent e = new ActivityEvent();
            e.id = d.getId();
            e.type = safeStr(d.getString("type"));
            Number p = d.getLong("points");
            e.points = p == null ? 0 : p.intValue();
            e.storeName = safeStr(d.getString("storeName"));
            e.ts = d.getTimestamp("ts");
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safeStr(String s) { return s == null ? "" : s; }
}
