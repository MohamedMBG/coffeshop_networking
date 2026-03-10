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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.MenuItemModel;
import com.example.loyaltyapp.ui.MenuAdapter;
import com.example.loyaltyapp.viewmodels.HomeViewModel;
import com.google.firebase.Timestamp;

import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private com.example.loyaltyapp.databinding.FragmentHomeBinding binding;

    // Menu UI
    private RecyclerView menuRv;
    private Chip chipAll, chipCoffee, chipTea, chipPastries, chipBreakfast, chipLunch;

    // Banner UI
    private View bannerCard;
    private View bannerRoot;
    private TextView tvBannerBadge, tvBannerTitle, tvBannerSubtitle;
    private ImageView ivBannerIcon;

    private MenuAdapter adapter;
    private HomeViewModel viewModel;

    public HomeFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Assign ViewModel
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // ---- Bind menu views
        menuRv = binding.menuRecyclerView;
        chipAll = binding.chipAll;
        chipCoffee = binding.chipCoffee;
        chipTea = binding.chipTea;
        chipPastries = binding.chipPastries;
        chipBreakfast = binding.chipBreakfast;
        chipLunch = binding.chipLunch;

        // ---- Bind banner views
        bannerCard = binding.bannerCard;
        bannerRoot = binding.bannerRoot;
        tvBannerBadge = binding.tvBannerBadge;
        tvBannerTitle = binding.tvBannerTitle;
        tvBannerSubtitle = binding.tvBannerSubtitle;
        ivBannerIcon = binding.ivBannerIcon;

        // ---- RecyclerView
        menuRv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new MenuAdapter(new ArrayList<MenuItemModel>(), new MenuAdapter.OnItemClick() {
            @Override
            public void onClick(@NonNull MenuItemModel item) {
                // TODO: open item details / add to cart
            }
        });
        menuRv.setAdapter(adapter);

        // ---- Category filters
        chipAll.setOnClickListener(vw -> viewModel.loadCategory(null));
        chipCoffee.setOnClickListener(vw -> viewModel.loadCategory("Coffee"));
        chipTea.setOnClickListener(vw -> viewModel.loadCategory("Tea"));
        chipPastries.setOnClickListener(vw -> viewModel.loadCategory("Pastries"));
        chipBreakfast.setOnClickListener(vw -> viewModel.loadCategory("Breakfast"));
        chipLunch.setOnClickListener(vw -> viewModel.loadCategory("Lunch"));

        // ---- Observe ViewModel Data! (MVVM Binding)
        viewModel.getMenuData().observe(getViewLifecycleOwner(), items -> {
            if (items != null) {
                adapter.submit(items);
            }
        });

        viewModel.getBannerConfigData().observe(getViewLifecycleOwner(), this::bindBanner);

        // ---- Punch Card gamification binding
        viewModel.getUserData().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                updatePunchCard(user.getVisits());
            }
        });
    }

    private void updatePunchCard(int visits) {
        // Evaluate if they have a completed reward (visits mod 10 == 0 but more than 0
        // visits)
        boolean hasRewardEarned = visits > 0 && (visits % 10 == 0);

        // If they just hit exactly a multiple of 10, visually show them 10 FULL cups
        // instead of 0!
        int cupsFilled = hasRewardEarned ? 10 : visits % 10;

        for (int i = 0; i < 10; i++) {
            android.widget.ImageView cup = (android.widget.ImageView) binding.gridPunchCard.getChildAt(i);
            if (cup == null)
                continue;

            if (i < cupsFilled) {
                cup.setImageResource(R.drawable.ic_coffee_cup_filled);
                // remove highlight background if any, mostly for cup 10 but harmless for others
                cup.setBackground(null);
            } else {
                cup.setImageResource(R.drawable.ic_coffee_cup_empty);
                if (i == 9) { // The reward cup
                    cup.setBackgroundResource(R.drawable.circle_background_bonus);
                } else {
                    cup.setBackground(null);
                }
            }
        }

        if (hasRewardEarned) {
            // Re-style UI slightly to congratulate them!
            binding.punchCardView.setCardBackgroundColor(0xFFFFF9C4); // Light Yellow to highlight
            binding.tvPunchCardSubtitle.setText(R.string.punch_card_reward_earned_subtitle);
            if (binding.punchCardView.findViewById(R.id.tvPunchCardTitle) != null) {
                ((android.widget.TextView) binding.punchCardView.findViewById(R.id.tvPunchCardTitle))
                        .setText(R.string.punch_card_reward_ready);
            }
        } else {
            // Restore normal look
            binding.punchCardView.setCardBackgroundColor(getResources().getColor(android.R.color.white, null));
            int needed = 10 - cupsFilled;
            binding.tvPunchCardSubtitle.setText(getString(R.string.punch_card_needed, needed));
            if (binding.punchCardView.findViewById(R.id.tvPunchCardTitle) != null) {
                ((android.widget.TextView) binding.punchCardView.findViewById(R.id.tvPunchCardTitle))
                        .setText(R.string.punch_card_title);
            }
        }
    }

    // ===================== Banner (UI Binding logic) =====================

    private void bindBanner(@Nullable Map<String, Object> cfg) {
        if (cfg == null) {
            if (bannerCard != null)
                bannerCard.setVisibility(View.GONE);
            return;
        }

        boolean active = getBool(cfg.get("active"), true);
        Long startMs = getMillis(cfg.get("startAt"));
        Long endMs = getMillis(cfg.get("endAt"));
        long now = System.currentTimeMillis();
        boolean inWindow = (startMs == null || now >= startMs) && (endMs == null || now <= endMs);

        if (!active || !inWindow) {
            bannerCard.setVisibility(View.GONE);
            return;
        }
        bannerCard.setVisibility(View.VISIBLE);

        String badge = getStr(cfg.get("badge"), "Special Offer");
        String title = getStr(cfg.get("title"), "Buy 1 Get 1 Free");
        String subtitle = getStr(cfg.get("subtitle"), "");
        String textHex = getStr(cfg.get("textColor"), "#FFFFFF");
        String cStart = getStr(cfg.get("startColor"), "#FF7A00");
        String cEnd = getStr(cfg.get("endColor"), "#FF3D00");
        String iconUrl = getStr(cfg.get("iconUrl"), "");
        int iconVersion = (cfg.get("iconVersion") instanceof Number)
                ? ((Number) cfg.get("iconVersion")).intValue()
                : 0;
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
        int end = safeColor(cEnd, Color.parseColor("#FF3D00"));
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { start, end });
        gd.setCornerRadius(dp(16));
        bannerRoot.setBackground(gd);

        // Remote icon with Glide
        if (iconUrl != null && !iconUrl.isEmpty()) {
            ivBannerIcon.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(iconUrl)
                    .placeholder(R.drawable.ic_offer)
                    .error(R.drawable.ic_offer)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .signature(new ObjectKey(iconVersion))
                    .into(ivBannerIcon);
        } else {
            ivBannerIcon.setImageResource(R.drawable.ic_offer);
            ivBannerIcon.setVisibility(View.VISIBLE);
        }

        // Click → deeplink
        bannerCard.setOnClickListener(view -> {
            if (!deeplink.isEmpty()) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(deeplink)));
                } catch (Exception ex) {
                    Log.w(TAG, "Deeplink failed: " + deeplink, ex);
                }
            }
        });
    }

    // ================================ Helpers
    // ======================================

    private String getStr(Object v, String def) {
        return (v instanceof String && !((String) v).isEmpty()) ? (String) v : def;
    }

    private boolean getBool(Object v, boolean def) {
        return (v instanceof Boolean) ? ((Boolean) v) : def;
    }

    @Nullable
    private Long getMillis(Object v) {
        if (v instanceof Timestamp)
            return ((Timestamp) v).toDate().getTime();
        if (v instanceof Date)
            return ((Date) v).getTime();
        return null;
    }

    private int safeColor(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float dp(int dps) {
        return dps * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
