package com.example.loyaltyapp.data.repository;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.loyaltyapp.models.MenuItemModel;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MenuRepository {
    private static final String TAG = "MenuRepository";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration currentReg;

    public void listenToPopularItems(MutableLiveData<List<MenuItemModel>> liveData) {
        Query query = db.collection("menu_items")
                .whereEqualTo("isAvailable", true)
                .whereEqualTo("isPopular", true)
                .limit(12);
        attachQuery(query, liveData);
    }

    public void listenToCategory(String category, MutableLiveData<List<MenuItemModel>> liveData) {
        Query query = db.collection("menu_items")
                .whereEqualTo("isAvailable", true)
                .whereEqualTo("category", category)
                .orderBy("name")
                .limit(24);
        attachQuery(query, liveData);
    }

    private void attachQuery(@NonNull Query query, MutableLiveData<List<MenuItemModel>> liveData) {
        if (currentReg != null) {
            currentReg.remove();
            currentReg = null;
        }

        currentReg = query.addSnapshotListener((snap, err) -> {
            if (err != null) {
                Log.e(TAG, "Query failed", err);
                return;
            }
            List<MenuItemModel> items = new ArrayList<>();
            if (snap != null) {
                for (DocumentSnapshot d : snap.getDocuments()) {
                    MenuItemModel m = d.toObject(MenuItemModel.class);
                    if (m != null) {
                        m.setId(d.getId());
                        items.add(m);
                    }
                }
            }
            liveData.postValue(items);
        });
    }

    public void cleanup() {
        if (currentReg != null) {
            currentReg.remove();
            currentReg = null;
        }
    }
}
