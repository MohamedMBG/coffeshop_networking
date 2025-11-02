package com.example.loyaltyapp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.MenuItemModel;
import com.example.loyaltyapp.ui.MenuAdapter;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView menuRv;
    private Chip chipAll, chipCoffee, chipTea, chipPastries, chipBreakfast, chipLunch;

    private FirebaseFirestore db;
    private MenuAdapter adapter;
    private ListenerRegistration currentReg;

    @Nullable private String selectedCategory = null; // null or "Coffee","Tea","Pastries","Breakfast","Lunch"

    public HomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Views
        menuRv = v.findViewById(R.id.menuRecyclerView);
        chipAll = v.findViewById(R.id.chipAll);
        chipCoffee = v.findViewById(R.id.chipCoffee);
        chipTea = v.findViewById(R.id.chipTea);
        chipPastries = v.findViewById(R.id.chipPastries);
        chipBreakfast = v.findViewById(R.id.chipBreakfast);
        chipLunch = v.findViewById(R.id.chipLunch);

        // RecyclerView
        menuRv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new MenuAdapter(new ArrayList<>(), item -> {
            // TODO: open item details / add to cart
        });
        menuRv.setAdapter(adapter);

        // Firestore
        db = FirebaseFirestore.getInstance();

        // Default: load Popular Items
        loadPopular();

        // Category listeners (no search involved)
        chipAll.setOnClickListener(vw -> {
            selectedCategory = null;
            loadPopular();
        });
        chipCoffee.setOnClickListener(vw -> applyCategory("Coffee"));
        chipTea.setOnClickListener(vw -> applyCategory("Tea"));
        chipPastries.setOnClickListener(vw -> applyCategory("Pastries"));
        chipBreakfast.setOnClickListener(vw -> applyCategory("Breakfast"));
        chipLunch.setOnClickListener(vw -> applyCategory("Lunch"));
    }

    private void applyCategory(@NonNull String category) {
        selectedCategory = category;
        loadByCategory(category);
    }

    private void loadPopular() {
        attachQuery(
                db.collection("menu_items")
                        .whereEqualTo("isAvailable", true)
                        .whereEqualTo("isPopular", true)
                        .limit(12) // keep simple; add orderBy("name") later if you want (may need index)
        );
    }

    private void loadByCategory(@NonNull String category) {
        attachQuery(
                db.collection("menu_items")
                        .whereEqualTo("isAvailable", true)
                        .whereEqualTo("category", category)
                        .orderBy("name")
                        .limit(24)
        );
    }

    /** Attaches a snapshot listener to the given query, replacing any previous one. */
    private void attachQuery(@NonNull Query query) {
        if (currentReg != null) {
            currentReg.remove();
            currentReg = null;
        }

        currentReg = query.addSnapshotListener((snap, err) -> {
            if (!isAdded()) return;
            if (err != null) {
                Log.e("HomeFragment", "Query failed", err);
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
            adapter.submit(items);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentReg != null) {
            currentReg.remove();
            currentReg = null;
        }
    }
}
