package com.example.loyaltyapp.data.repository;

import com.example.loyaltyapp.models.ActivityEvent;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ActivityRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnStatsLoaded {
        void onSuccess(int points, int visits);
        void onError(Exception e);
    }

    public interface OnActivitiesLoaded {
        void onSuccess(List<ActivityEvent> events);
        void onError(Exception e);
    }

    public void getUserStats(String uid, OnStatsLoaded callback) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    int points = doc.contains("points") ? safeInt(doc.getLong("points")) : 0;
                    int visits = doc.contains("visits") ? safeInt(doc.getLong("visits")) : 0;
                    callback.onSuccess(points, visits);
                })
                .addOnFailureListener(callback::onError);
    }

    public void getActivityHistory(String uid, OnActivitiesLoaded callback) {
        db.collection("users").document(uid)
                .collection("activities")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(snap -> {
                    List<ActivityEvent> events = new ArrayList<>();
                    for (DocumentSnapshot d : snap) {
                        ActivityEvent ev = ActivityEvent.fromDoc(d);
                        if (ev != null) {
                            events.add(ev);
                        }
                    }
                    callback.onSuccess(events);
                })
                .addOnFailureListener(callback::onError);
    }

    private int safeInt(Long n) {
        return n == null ? 0 : n.intValue();
    }
}
