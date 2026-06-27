package com.example.loyaltyapp.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.adapters.RewardAdapter;
import com.example.loyaltyapp.data.repository.RewardsRepository;
import com.example.loyaltyapp.models.Rewards;
import com.example.loyaltyapp.viewmodels.RewardsViewModel;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewarsdFragment extends Fragment {

    private static final String TAG = "RewardsFragment";

    private com.example.loyaltyapp.databinding.FragmentRewarsdBinding binding;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View emptyState, loadingOverlay;
    private TextView tvPointsHeader, tvNextRewardInfo;
    private CircularProgressIndicator progressToNext;
    private ChipGroup chipGroup;
    
    private RewardsViewModel viewModel;

    private RewardAdapter adapter;
    private int userPoints = 0;

    public RewarsdFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentRewarsdBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        swipeRefresh = binding.swipeRefresh;
        recycler = binding.rewardsRecycler;
        emptyState = binding.emptyState;
        loadingOverlay = binding.loadingOverlay;
        tvPointsHeader = binding.tvPointsHeader;
        tvNextRewardInfo = binding.tvNextRewardInfo;
        progressToNext = binding.progressToNext;
        chipGroup = binding.chipGroupFilters;

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);
        adapter = new RewardAdapter(() -> userPoints, this::onRedeemClicked);
        recycler.setAdapter(adapter);
        
        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(RewardsViewModel.class);

        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                viewModel.setFilter("all");
            } else {
                int id = ids.get(0);
                if (id == R.id.chipFood)
                    viewModel.setFilter("Food");
                else if (id == R.id.chipDrinks)
                    viewModel.setFilter("Drinks");
                else if (id == R.id.chipExclusive)
                    viewModel.setFilter("Exclusive");
                else
                    viewModel.setFilter("all");
            }
        });

        // Observe Data
        viewModel.getRewards().observe(getViewLifecycleOwner(), this::applyRewardsList);
        
        viewModel.getUserPoints().observe(getViewLifecycleOwner(), points -> {
            userPoints = points != null ? points : 0;
            updateHeader(userPoints);
            // Trigger an adapter update if points change so buttons can refresh their enabled state
            if (adapter != null) adapter.notifyDataSetChanged();
        });

        viewModel.getRedemptionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            if (state.isFinished) {
                if (state.isSuccess) {
                    Toast.makeText(requireContext(), "Reward Redeemed Successfully!", Toast.LENGTH_SHORT).show();
                } else if (state.error != null) {
                    Toast.makeText(requireContext(), "Redeem failed: " + state.error, Toast.LENGTH_LONG).show();
                }
                viewModel.resetRedemptionState();
            }
        });
        
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), this::showLoading);
        
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), "Error: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateHeader(int points) {
        tvPointsHeader.setText(points + " points");
        progressToNext.setProgress(0);
        tvNextRewardInfo.setText("");
        // We calculate remaining points based on cheapest reward inside applyRewardsList now
    }
    
    private void applyRewardsList(List<Rewards> list) {
        if (list == null) list = new ArrayList<>();
        
        adapter.submitList(list);
        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        if (!list.isEmpty()) {
            int cheapest = list.get(0).redeemPoints;
            for (Rewards r : list) {
                if (r.redeemPoints < cheapest) cheapest = r.redeemPoints;
            }
            
            int remaining = Math.max(0, cheapest - userPoints);
            tvNextRewardInfo.setText(remaining == 0 ? "You can redeem now" : (remaining + " pts to your first reward"));
            int pct = (int) (100f * Math.min(1f, userPoints / (float) Math.max(1, cheapest)));
            progressToNext.setProgress(pct);
        } else {
            tvNextRewardInfo.setText("");
            progressToNext.setProgress(0);
        }
    }

    private static String safeString(String s) {
        return (s == null) ? "" : s;
    }

    // Redemption is intentionally disabled until backend issues a redeem
    // code and confirms it at the cashier. Do NOT call viewModel.redeemReward
    // from the client — Firestore rules block client-side points mutation
    // and any deduction here would only succeed against an unsecured DB.
    private void onRedeemClicked(@NonNull Rewards r) {
        if (userPoints < r.redeemPoints) {
            Toast.makeText(requireContext(), "Not enough points yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Snackbar.make(requireView(),
                "Redemption coming soon. Your points are safe.",
                Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setAction("OK", v -> { })
                .show();
    }

        // showLoadError is no longer needed since errors are handled by observing getErrorMessage() in onViewCreated


    private void showLoading(boolean show) {
        swipeRefresh.setRefreshing(false);
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
