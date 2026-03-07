package com.example.loyaltyapp.data.repository;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration userListener;

    public void listenToUser(String uid, MutableLiveData<DocumentSnapshot> liveData) {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }

        userListener = db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Failed to load profile", e);
                liveData.postValue(null);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                liveData.postValue(snapshot);
            }
        });
    }

    public void saveProfile(String uid, Map<String, Object> updateData,
            OnSaveCompleteListener listener) {
        updateData.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("users").document(uid)
                .set(updateData, SetOptions.merge())
                .addOnSuccessListener(unused -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    public void cleanup() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    public interface OnSaveCompleteListener {
        void onSuccess();

        void onFailure(String error);
    }
}
