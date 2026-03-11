package com.example.loyaltyapp.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.loyaltyapp.LoyaltyActivity;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.adapters.ActivityAdapter;
import com.example.loyaltyapp.models.ActivityEvent;
import com.example.loyaltyapp.viewmodels.ActivityViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ActivityFragment extends Fragment {

    private com.example.loyaltyapp.databinding.FragmentActivityBinding binding;
    private ActivityViewModel viewModel;

    // UI
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View emptyState;
    private ProgressBar loading;
    private TextView tvCurrentPoints, tvTotalVisits, tvLastScan, tvUserLevel;
    private ImageView btnInfo;
    private Button btnScanNow;

    private ChipGroup chipGroupType, chipGroupDate;
    private Chip chipAll, chipScans, chipRedemptions, chipBonuses;
    private Chip chipThisWeek, chipThisMonth, chipCustomDate;

    // Data
    private final List<ActivityEvent> shownEvents = new ArrayList<>();
    private ActivityAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentActivityBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        // ViewModel
        viewModel = new ViewModelProvider(this).get(ActivityViewModel.class);

        // Header
        tvCurrentPoints = binding.tvCurrentPoints;
        tvTotalVisits = binding.tvTotalVisits;
        tvLastScan = binding.tvLastScan;
        tvUserLevel = binding.tvUserLevel;
        btnInfo = binding.btnInfo;

        // List/empty/loading
        swipeRefresh = binding.swipeRefresh;
        recycler = binding.activityRecyclerView;
        emptyState = binding.emptyState;
        loading = binding.loadingIndicator;
        btnScanNow = binding.btnScanNow;

        // Chips
        chipGroupType = binding.chipGroupType;
        chipGroupDate = binding.chipGroupDate;
        chipAll = binding.chipAll;
        chipScans = binding.chipScans;
        chipRedemptions = binding.chipRedemptions;
        chipBonuses = binding.chipBonuses;
        chipThisWeek = binding.chipThisWeek;
        chipThisMonth = binding.chipThisMonth;
        chipCustomDate = binding.chipCustomDate;

        // Recycler
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ActivityAdapter(shownEvents);
        recycler.setAdapter(adapter);

        // Pull-to-refresh
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadData());

        // Chip listeners
        chipGroupType.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == chipAll.getId()) viewModel.setTypeFilter("all");
            else if (id == chipScans.getId()) viewModel.setTypeFilter("scan");
            else if (id == chipRedemptions.getId()) viewModel.setTypeFilter("redemption");
            else if (id == chipBonuses.getId()) viewModel.setTypeFilter("bonus");
        });

        chipGroupDate.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) { 
                viewModel.resetDateRange();
                return;
            }
            int id = ids.get(0);
            if (id == chipThisWeek.getId()) {
                viewModel.setThisWeekRange();
            } else if (id == chipThisMonth.getId()) {
                viewModel.setThisMonthRange();
            } else if (id == chipCustomDate.getId()) {
                openCustomRangePicker();
            }
        });

        // Info click: tiny toast/help
        btnInfo.setOnClickListener(vw -> Toast.makeText(requireContext(),
                "Earn points by scanning in-store. Redeem rewards from your points.", Toast.LENGTH_SHORT).show());

        // Scan now → switch to Scan tab
        btnScanNow.setOnClickListener(vw -> {
            if (getActivity() instanceof LoyaltyActivity) {
                ((LoyaltyActivity) getActivity()).openScanTab();
            }
        });

        observeViewModel();
        viewModel.loadData();

        return v;
    }

    private void observeViewModel() {
        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null && isLoading) {
                loading.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
            } else {
                loading.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null && !err.isEmpty()) {
                Toast.makeText(requireContext(), "Error: " + err, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getUserStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                tvCurrentPoints.setText(String.valueOf(stats.points));
                tvTotalVisits.setText(String.valueOf(stats.visits));
                tvUserLevel.setText(levelFor(stats.points));
            }
        });

        viewModel.getLastScanTime().observe(getViewLifecycleOwner(), date -> {
            if (date != null) {
                tvLastScan.setText(relativeTime(date));
            } else {
                tvLastScan.setText("—");
            }
        });

        viewModel.getDisplayedEvents().observe(getViewLifecycleOwner(), events -> {
            if (events != null) {
                shownEvents.clear();
                shownEvents.addAll(events);
                adapter.notifyDataSetChanged();
                emptyState.setVisibility(shownEvents.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void openCustomRangePicker() {
        final Calendar start = Calendar.getInstance();
        final Calendar end = Calendar.getInstance();

        DatePickerDialog dpStart = new DatePickerDialog(requireContext(), (v, y, m, d) -> {
            start.set(y, m, d, 0, 0, 0);
            DatePickerDialog dpEnd = new DatePickerDialog(requireContext(), (v2, y2, m2, d2) -> {
                end.set(y2, m2, d2, 23, 59, 59);
                viewModel.setDateRange(start.getTime(), end.getTime());
            }, end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH));
            dpEnd.show();
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
        dpStart.show();
    }

    private static String levelFor(int points) {
        if (points >= 200) return "Gold";
        if (points >= 100) return "Silver";
        return "Bronze";
    }

    private static String relativeTime(Date date) {
        long diff = System.currentTimeMillis() - date.getTime();
        long mins = diff / 60000;
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + " h ago";
        long days = hrs / 24;
        return days + " days ago";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
