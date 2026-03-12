package com.example.loyaltyapp.data.repository;

import android.util.Log;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;

public class ConfigRepository {
    private static final String TAG = "ConfigRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration bannerListener;
    private ListenerRegistration appStatusListener;

    public void listenToBannerConfig(MutableLiveData<Map<String, Object>> liveData) {
        if (bannerListener != null) {
            bannerListener.remove();
            bannerListener = null;
        }

        bannerListener = db.collection("config").document("home_banner")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        Log.e(TAG, "Banner config failed or missing", e);
                        liveData.postValue(null);
                        return;
                    }
                    liveData.postValue(snapshot.getData());
                });
    }

    public void listenToAppStatus(MutableLiveData<Map<String, Object>> liveData) {
        if (appStatusListener != null) {
            appStatusListener.remove();
            appStatusListener = null;
        }

        appStatusListener = db.collection("meta").document("app_status")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        Log.e(TAG, "App status config failed or missing", e);
                        liveData.postValue(null);
                        return;
                    }
                    liveData.postValue(snapshot.getData());
                });
    }

    public void cleanup() {
        if (bannerListener != null) {
            bannerListener.remove();
            bannerListener = null;
        }
        if (appStatusListener != null) {
            appStatusListener.remove();
            appStatusListener = null;
        }
    }
}
