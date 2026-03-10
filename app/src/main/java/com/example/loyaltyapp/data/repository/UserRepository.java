package com.example.loyaltyapp.data.repository;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;

import com.example.loyaltyapp.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    public void listenToUser(String uid, MutableLiveData<User> liveData) {
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
                User user = snapshot.toObject(User.class);
                if (user != null) {
                    // uid might not be in the document itself, so set it manually just in case
                    user.setUid(uid);
                    liveData.postValue(user);
                } else {
                    liveData.postValue(null);
                }
            } else {
                liveData.postValue(null);
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
