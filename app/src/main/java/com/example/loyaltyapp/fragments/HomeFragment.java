package com.example.loyaltyapp.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.MenuItemModel;
import com.example.loyaltyapp.ui.MenuAdapter;
import com.google.android.material.chip.Chip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    // Menu UI
    private RecyclerView menuRv;
    private Chip chipAll, chipCoffee, chipTea, chipPastries, chipBreakfast, chipLunch;

    // Banner UI
    private View bannerCard;
    private View bannerRoot;
    private TextView tvBannerBadge, tvBannerTitle, tvBannerSubtitle;
    private ImageView ivBannerIcon;

    // Data
    private FirebaseFirestore db;
    private MenuAdapter adapter;
    private ListenerRegistration currentReg;      // menu query listener
    private ListenerRegistration bannerListener;  // banner config listener
    @Nullable private String selectedCategory = null;

    public HomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // ---- Bind menu views
        menuRv        = v.findViewById(R.id.menuRecyclerView);
        chipAll       = v.findViewById(R.id.chipAll);
        chipCoffee    = v.findViewById(R.id.chipCoffee);
        chipTea       = v.findViewById(R.id.chipTea);
        chipPastries  = v.findViewById(R.id.chipPastries);
        chipBreakfast = v.findViewById(R.id.chipBreakfast);
        chipLunch     = v.findViewById(R.id.chipLunch);

        // ---- Bind banner views (ensure these IDs exist in XML)
        bannerCard       = v.findViewById(R.id.bannerCard);
        bannerRoot       = v.findViewById(R.id.bannerRoot);
        tvBannerBadge    = v.findViewById(R.id.tvBannerBadge);
        tvBannerTitle    = v.findViewById(R.id.tvBannerTitle);
        tvBannerSubtitle = v.findViewById(R.id.tvBannerSubtitle);
        ivBannerIcon     = v.findViewById(R.id.ivBannerIcon);

        // ---- RecyclerView
        menuRv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new MenuAdapter(new ArrayList<MenuItemModel>(), new MenuAdapter.OnItemClick() {
            @Override public void onClick(@NonNull MenuItemModel item) {
                // TODO: open item details / add to cart
            }
        });
        menuRv.setAdapter(adapter);

        // ---- Firestore
        db = FirebaseFirestore.getInstance();

        // Load popular on first render
        loadPopular();

        // ---- Category filters
        chipAll.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { selectedCategory = null; loadPopular(); }});
        chipCoffee.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { applyCategory("Coffee"); }});
        chipTea.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { applyCategory("Tea"); }});
        chipPastries.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { applyCategory("Pastries"); }});
        chipBreakfast.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { applyCategory("Breakfast"); }});
        chipLunch.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View vw) { applyCategory("Lunch"); }});

        // ---- Start real-time banner listener
        startBannerListener();
    }

    // ===================== Banner (remote-config via Firestore) =====================

    private void startBannerListener() {
        if (bannerListener != null) {
            bannerListener.remove();
            bannerListener = null;
        }
        bannerListener = db.collection("config").document("home_banner")
                .addSnapshotListener((snapshot, e) -> {
                    if (!isAdded()) return;
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        if (bannerCard != null) bannerCard.setVisibility(View.GONE);
                        return;
                    }
                    bindBanner(snapshot.getData());
                });
    }

    private void bindBanner(@Nullable Map<String, Object> cfg) {
        if (cfg == null) {
            if (bannerCard != null) bannerCard.setVisibility(View.GONE);
            return;
        }

        boolean active = getBool(cfg.get("active"), true);
        Long startMs = getMillis(cfg.get("startAt"));
        Long endMs   = getMillis(cfg.get("endAt"));
        long now = System.currentTimeMillis();
        boolean inWindow = (startMs == null || now >= startMs) && (endMs == null || now <= endMs);

        if (!active || !inWindow) {
            bannerCard.setVisibility(View.GONE);
            return;
        }
        bannerCard.setVisibility(View.VISIBLE);

        String badge    = getStr(cfg.get("badge"), "Special Offer");
        String title    = getStr(cfg.get("title"), "Buy 1 Get 1 Free");
        String subtitle = getStr(cfg.get("subtitle"), "");
        String textHex  = getStr(cfg.get("textColor"), "#FFFFFF");
        String cStart   = getStr(cfg.get("startColor"), "#FF7A00");
        String cEnd     = getStr(cfg.get("endColor"),   "#FF3D00");
        String iconUrl  = getStr(cfg.get("iconUrl"), "");
        int iconVersion = (cfg.get("iconVersion") instanceof Number)
                ? ((Number) cfg.get("iconVersion")).intValue() : 0;
        final String deeplink = getStr(cfg.get("deeplink"), "");

        // Texts
        tvBannerBadge.setText(badge);
        tvBannerTitle.setText(title);
        tvBannerSubtitle.setText(subtitle);

        // Text color
        int textColor = safeColor(textHex, Color.WHITE);
        tvBannerBadge.setTextColor(textColor);
        tvBannerTitle.setTextColor(textColor);
        tvBannerSubtitle.setTextColor(textColor);

        // Dynamic gradient background
        int start = safeColor(cStart, Color.parseColor("#FF7A00"));
        int end   = safeColor(cEnd,   Color.parseColor("#FF3D00"));
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ start, end });
        gd.setCornerRadius(dp(16));
        bannerRoot.setBackground(gd);

        // Remote icon with Glide (+ cache buster)
        if (iconUrl != null && iconUrl.length() > 0) {
            ivBannerIcon.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(iconUrl) // must be a direct HTTPS image URL
                    .placeholder(R.drawable.ic_offer)
                    .error(R.drawable.ic_offer)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .signature(new ObjectKey(iconVersion)) // bump this in Firestore when you change the image
                    .into(ivBannerIcon);
        } else {
            ivBannerIcon.setImageResource(R.drawable.ic_offer);
            ivBannerIcon.setVisibility(View.VISIBLE);
        }

        // Click → deeplink (optional)
        bannerCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (!deeplink.isEmpty()) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(deeplink)));
                    } catch (Exception ex) {
                        Log.w(TAG, "Deeplink failed: " + deeplink, ex);
                    }
                }
            }
        });
    }

    // ========================== Menu (your existing logic) ==========================

    private void applyCategory(@NonNull String category) {
        selectedCategory = category;
        loadByCategory(category);
    }

    private void loadPopular() {
        attachQuery(
                db.collection("menu_items")
                        .whereEqualTo("isAvailable", true)
                        .whereEqualTo("isPopular", true)
                        .limit(12)
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
        currentReg = query.addSnapshotListener((QuerySnapshot snap, FirebaseFirestoreException err) -> {
            if (!isAdded()) return;
            if (err != null) {
                Log.e(TAG, "Query failed", err);
                return;
            }
            List<MenuItemModel> items = new ArrayList<MenuItemModel>();
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

    // ================================ Helpers ======================================

    private String getStr(Object v, String def) {
        return (v instanceof String && ((String) v).length() > 0) ? (String) v : def;
    }

    private boolean getBool(Object v, boolean def) {
        return (v instanceof Boolean) ? ((Boolean) v) : def;
    }

    @Nullable
    private Long getMillis(Object v) {
        if (v instanceof Timestamp) return ((Timestamp) v).toDate().getTime();
        if (v instanceof Date) return ((Date) v).getTime();
        return null;
    }

    private int safeColor(String hex, int fallback) {
        try { return Color.parseColor(hex); }
        catch (Exception ignored) { return fallback; }
    }

    private float dp(int dps) {
        return dps * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentReg != null) {
            currentReg.remove();
            currentReg = null;
        }
        if (bannerListener != null) {
            bannerListener.remove();
            bannerListener = null;
        }
    }
}
