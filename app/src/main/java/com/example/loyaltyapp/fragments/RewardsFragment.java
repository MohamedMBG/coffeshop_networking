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

public class RewardsFragment extends Fragment {

    private static final String TAG = "RewardsFragment";

    private com.example.loyaltyapp.databinding.FragmentRewardsBinding binding;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View emptyState, loadingOverlay;
    private TextView tvPointsHeader, tvNextRewardInfo;
    private CircularProgressIndicator progressToNext;
    private ChipGroup chipGroup;
    
    private RewardsViewModel viewModel;

    private RewardAdapter adapter;
    private int userPoints = 0;

    public RewardsFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = com.example.loyaltyapp.databinding.FragmentRewardsBinding.inflate(inflater, container, false);
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
        adapter = new RewardAdapter(() -> viewModel != null
                && Boolean.TRUE.equals(viewModel.getIsMutating().getValue()) ? -1 : userPoints,
                this::onRedeemClicked);
        recycler.setAdapter(adapter);
        
        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(RewardsViewModel.class);
        com.google.firebase.auth.FirebaseUser currentUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        viewModel.init(currentUser == null ? null : currentUser.getUid());

        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
        binding.btnMyRewards.setOnClickListener(button -> viewModel.recoverPendingReward(true));
        viewModel.getCheckingPending().observe(getViewLifecycleOwner(), checking -> {
            binding.btnMyRewards.setEnabled(!Boolean.TRUE.equals(checking)
                    && !Boolean.TRUE.equals(viewModel.getIsMutating().getValue()));
            binding.btnMyRewards.setText(Boolean.TRUE.equals(checking) ? "Checking…" : "My Rewards");
        });

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

        viewModel.getRedemptionState().observe(getViewLifecycleOwner(), event -> {
            if (isHidden() || getParentFragmentManager().isStateSaved()) return;
            RewardsViewModel.RedemptionState state =
                    event == null ? null : event.getContentIfNotHandled();
            if (state == null) return; // already handled or no event
            androidx.fragment.app.Fragment existing = getParentFragmentManager().findFragmentByTag("redeem_code");
            if (state.isSuccess && existing instanceof com.example.loyaltyapp.RedeemCodeDialog) {
                ((com.example.loyaltyapp.RedeemCodeDialog) existing).dismiss();
            }
            if (state.isSuccess && state.code != null) {
                // Backend deducted the points and issued a pending code; show it
                // as a QR for the cashier to scan.
                if (!getParentFragmentManager().isStateSaved()) {
                    com.example.loyaltyapp.RedeemCodeDialog
                            .newInstance(state.code, state.expiresAtEpochMs)
                            .show(getParentFragmentManager(), "redeem_code");
                }
            } else if (state.error != null) {
                Toast.makeText(requireContext(), "Redeem failed: " + state.error, Toast.LENGTH_LONG).show();
            }
        });
        
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), this::showLoading);
        viewModel.getIsMutating().observe(getViewLifecycleOwner(), busy -> {
            if (adapter != null) adapter.notifyDataSetChanged();
            binding.btnMyRewards.setEnabled(!Boolean.TRUE.equals(busy)
                    && !Boolean.TRUE.equals(viewModel.getCheckingPending().getValue()));
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), "Error: " + msg, Toast.LENGTH_LONG).show();
            }
        });

        // The redeem dialog's "Cancel reward" button hands back the pending code;
        // the ViewModel performs the cancel/refund.
        getParentFragmentManager().setFragmentResultListener(
                com.example.loyaltyapp.RedeemCodeDialog.REQUEST_CANCEL,
                getViewLifecycleOwner(),
                (key, bundle) -> {
                    String code = bundle.getString(com.example.loyaltyapp.RedeemCodeDialog.RESULT_CODE);
                    if (code != null) viewModel.cancelRedeem(code);
                });

        viewModel.getCancelRefunded().observe(getViewLifecycleOwner(), event -> {
            Integer refunded = event == null ? null : event.getContentIfNotHandled();
            if (refunded == null) return; // already handled or no event
            Toast.makeText(requireContext(),
                    getString(R.string.redeem_cancelled_format, refunded), Toast.LENGTH_SHORT).show();
        });
    }

    private void updateHeader(int points) {
        tvPointsHeader.setText(points + " points");
        updateProgress(viewModel.getRewards().getValue());
    }
    
    private void applyRewardsList(List<Rewards> list) {
        if (list == null) list = new ArrayList<>();
        
        adapter.submitList(list);
        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        updateProgress(list);
    }

    private void updateProgress(List<Rewards> list) {
        if (list != null && !list.isEmpty()) {
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

    // Redeem through the backend: it deducts the points and returns a pending
    // code shown as a QR (see the redemptionState observer). The cashier scans
    // that code to complete the spend — the client never mutates points.
    private void onRedeemClicked(@NonNull Rewards r) {
        if (Boolean.TRUE.equals(viewModel.getIsMutating().getValue())) return;
        if (userPoints < r.redeemPoints) {
            Toast.makeText(requireContext(), "Not enough points yet", Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.redeemReward(r);
    }

        // showLoadError is no longer needed since errors are handled by observing getErrorMessage() in onViewCreated


    private void showLoading(boolean show) {
        swipeRefresh.setRefreshing(false);
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) recoverPending();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && binding != null && isResumed()) recoverPending();
    }

    private void recoverPending() {
        com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        viewModel.init(user == null ? null : user.getUid());
        viewModel.recoverPendingReward(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
