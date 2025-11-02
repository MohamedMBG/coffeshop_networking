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
import com.example.loyaltyapp.models.Rewards;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewarsdFragment extends Fragment {

    private static final String TAG = "RewardsFragment";

    // Firestore fields
    private static final String COL_REWARDS = "Rewards";
    private static final String COL_USERS   = "users";
    private static final String F_ACTIVE    = "active";
    private static final String F_CATEGORY  = "category";
    private static final String F_NAME      = "name";
    private static final String F_IMAGE     = "imagePath";
    private static final String F_PRICE     = "priceMAD";
    private static final String F_POINTS    = "redeemPoints";
    private static final String F_USERPTS   = "points";

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recycler;
    private View emptyState, loadingOverlay;
    private TextView tvPointsHeader, tvNextRewardInfo;
    private CircularProgressIndicator progressToNext;
    private ChipGroup chipGroup;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String uid = FirebaseAuth.getInstance().getCurrentUser() != null
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

    private RewardAdapter adapter;
    private int userPoints = 0;
    private String activeFilter = "all"; // all|Food|Drinks|Exclusive (match your data case)

    public RewarsdFragment() {}

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rewarsd, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        swipeRefresh      = v.findViewById(R.id.swipeRefresh);
        recycler          = v.findViewById(R.id.rewardsRecycler);
        emptyState        = v.findViewById(R.id.emptyState);
        loadingOverlay = v.findViewById(R.id.loadingOverlay); // ✅ correct
        tvPointsHeader    = v.findViewById(R.id.tvPointsHeader);
        tvNextRewardInfo  = v.findViewById(R.id.tvNextRewardInfo);
        progressToNext    = v.findViewById(R.id.progressToNext);
        chipGroup         = v.findViewById(R.id.chipGroupFilters);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setNestedScrollingEnabled(false);;
        adapter = new RewardAdapter(() -> userPoints, this::onRedeemClicked);
        recycler.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::reloadAll);

        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                activeFilter = "all";
            } else {
                int id = ids.get(0);
                if (id == R.id.chipFood) activeFilter = "Food";
                else if (id == R.id.chipDrinks) activeFilter = "Drinks";
                else if (id == R.id.chipExclusive) activeFilter = "Exclusive";
                else activeFilter = "all";
            }
            loadRewards();
        });

        reloadAll();
    }

    private void reloadAll() {
        showLoading(true);
        loadUserPoints(this::loadRewards);
    }

    private void loadUserPoints(@NonNull Runnable then) {
        if (uid == null) { userPoints = 0; updateHeader(0); then.run(); return; }

        db.collection(COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    Long p = doc.getLong(F_USERPTS);
                    userPoints = (p == null) ? 0 : p.intValue();
                    updateHeader(userPoints);
                    then.run();
                })
                .addOnFailureListener(e -> {
                    userPoints = 0;
                    updateHeader(0);
                    then.run();
                });
    }

    private void updateHeader(int points) {
        tvPointsHeader.setText(points + " points");
        progressToNext.setProgress(0);
        tvNextRewardInfo.setText("");
    }

    /** Build the base Firestore query for current filter. */
    private Query buildQuery(boolean withOrder) {
        Query q = db.collection(COL_REWARDS).whereEqualTo(F_ACTIVE, true);
        if (!"all".equalsIgnoreCase(activeFilter)) {
            q = q.whereEqualTo(F_CATEGORY, activeFilter);
        }
        if (withOrder) q = q.orderBy(F_POINTS, Direction.ASCENDING);
        return q;
    }

    private void loadRewards() {
        // First try with server + orderBy (fast/minimal client work)
        buildQuery(true)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(this::applyRewardsFromSnapshot)
                .addOnFailureListener(err -> {
                    // If Firestore requires a composite index, fall back to fetching
                    // without order and sort on-device to keep UI working.
                    if (err instanceof FirebaseFirestoreException &&
                            ((FirebaseFirestoreException) err).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {

                        Log.w(TAG, "Composite index missing. Falling back to client-side sort.", err);
                        buildQuery(false)
                                .get(com.google.firebase.firestore.Source.SERVER)
                                .addOnSuccessListener(snap -> applyRewardsFromSnapshot(snap, /*sortOnDevice=*/true))
                                .addOnFailureListener(this::showLoadError);
                    } else {
                        showLoadError(err);
                    }
                });
    }

    private void applyRewardsFromSnapshot(@NonNull QuerySnapshot snap) {
        applyRewardsFromSnapshot(snap, /*sortOnDevice=*/false);
    }

    private void applyRewardsFromSnapshot(@NonNull QuerySnapshot snap, boolean sortOnDevice) {
        List<Rewards> list = new ArrayList<>();
        int cheapest = Integer.MAX_VALUE;

        for (DocumentSnapshot d : snap.getDocuments()) {
            Rewards r = parseReward(d);
            if (r == null) continue;
            list.add(r);
        }

        if (sortOnDevice) {
            Collections.sort(list, Comparator.comparingInt(o -> o.redeemPoints));
        }

        if (!list.isEmpty()) {
            cheapest = list.get(0).redeemPoints;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).redeemPoints < cheapest) cheapest = list.get(i).redeemPoints;
            }
        }

        adapter.submitList(list);
        emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        if (cheapest != Integer.MAX_VALUE) {
            int remaining = Math.max(0, cheapest - userPoints);
            tvNextRewardInfo.setText(remaining == 0 ? "You can redeem now" : (remaining + " pts to your first reward"));
            int pct = (int) (100f * Math.min(1f, userPoints / (float) Math.max(1, cheapest)));
            progressToNext.setProgress(pct);
        } else {
            tvNextRewardInfo.setText("");
            progressToNext.setProgress(0);
        }

        showLoading(false);
    }

    @Nullable
    private Rewards parseReward(@NonNull DocumentSnapshot d) {
        Boolean active = d.getBoolean(F_ACTIVE);
        String name = d.getString(F_NAME);
        if (active == null || !active || name == null) return null;

        Rewards r = new Rewards();
        r.id = d.getId();
        r.name = name;
        r.imagePath = safeString(d.getString(F_IMAGE));
        r.active = true;

        // priceMAD can be Double or Long
        Object price = d.get(F_PRICE);
        if (price instanceof Double) r.priceMAD = (Double) price;
        else if (price instanceof Long) r.priceMAD = ((Long) price).doubleValue();
        else r.priceMAD = 0d;

        // redeemPoints can be Long or Double
        Object pts = d.get(F_POINTS);
        if (pts instanceof Long) r.redeemPoints = ((Long) pts).intValue();
        else if (pts instanceof Double) r.redeemPoints = ((Double) pts).intValue();
        else r.redeemPoints = 0;

        // optional category if your adapter needs it
        r.category = safeString(d.getString(F_CATEGORY));

        return r;
    }

    //redeeming reward
    private void redeemReward(@NonNull final Rewards r) {
        if (uid == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        final FirebaseFirestore db = FirebaseFirestore.getInstance();
        final DocumentReference userRef   = db.collection(COL_USERS).document(uid);
        final DocumentReference rewardRef = db.collection(COL_REWARDS).document(r.id);

        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(@NonNull Transaction tx) throws FirebaseFirestoreException {
                // 1) Read both docs
                DocumentSnapshot userSnap   = tx.get(userRef);
                DocumentSnapshot rewardSnap = tx.get(rewardRef);

                if (!userSnap.exists()) {
                    throw new FirebaseFirestoreException("User not found",
                            FirebaseFirestoreException.Code.NOT_FOUND);
                }
                if (!rewardSnap.exists()) {
                    throw new FirebaseFirestoreException("Reward not found",
                            FirebaseFirestoreException.Code.NOT_FOUND);
                }

                // 2) Current points (robust cast)
                Number currentNum = (Number) userSnap.get(F_USERPTS);
                long current = (currentNum == null) ? 0L : currentNum.longValue();

                // 3) Cost from DB (never trust UI)
                Number costNum = (Number) rewardSnap.get(F_POINTS);
                long cost = (costNum == null) ? 0L : costNum.longValue();

                Boolean active = rewardSnap.getBoolean(F_ACTIVE);
                if (active != null && !active) {
                    throw new FirebaseFirestoreException("Reward not active",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }
                if (current < cost) {
                    throw new FirebaseFirestoreException("Not enough points",
                            FirebaseFirestoreException.Code.FAILED_PRECONDITION);
                }

                // 4) Subtract points (either explicit value OR increment negative)
                long newPoints = current - cost;
                tx.update(userRef, F_USERPTS, newPoints);
                // Alternatively:
                // tx.update(userRef, F_USERPTS, FieldValue.increment(-cost));

                // 5) Log under users/{uid}/activities
                DocumentReference logRef = userRef.collection("activities").document();
                Map<String, Object> log = new HashMap<>();
                log.put("type", "redeem");
                log.put("rewardId", rewardSnap.getId());
                log.put("rewardName", rewardSnap.getString(F_NAME));
                log.put("points", cost);
                log.put("ts", Timestamp.now());
                tx.set(logRef, log);


                return null;
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                // Update local UI cache
                userPoints -= r.redeemPoints;
                if (userPoints < 0) userPoints = 0;
                updateHeader(userPoints);
                showLoading(false);
                Toast.makeText(requireContext(), "Redeemed: " + r.name, Toast.LENGTH_SHORT).show();
                adapter.notifyDataSetChanged(); // refresh buttons state
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                showLoading(false);
                Toast.makeText(requireContext(), "Redeem failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    private static String safeString(String s) { return (s == null) ? "" : s; }

    private void onRedeemClicked(@NonNull Rewards r) {
        if (userPoints < r.redeemPoints) {
            Toast.makeText(requireContext(), "Not enough points yet", Toast.LENGTH_SHORT).show();
            return;
        }
        // TODO: call your redeem endpoint / Cloud Function here.
        Snackbar.make(requireView(), "Redeem " + r.name + " for " + r.redeemPoints + " points: bientôt!", Snackbar.LENGTH_LONG)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .setAction("OK", v -> {})
                .show();
        redeemReward(r);
    }

    private void showLoadError(@NonNull Exception e) {
        showLoading(false);
        emptyState.setVisibility(View.VISIBLE);

        String msg;
        if (e instanceof FirebaseNetworkException) {
            msg = "No internet connection.";
        } else if (e instanceof FirebaseFirestoreException &&
                ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
            msg = "A Firestore index is required. You can also keep using the screen; we’ll sort locally.";
        } else {
            msg = e.getMessage();
        }
        Log.e(TAG, "Failed to load rewards", e);
        Toast.makeText(requireContext(), "Failed to load rewards: " + msg, Toast.LENGTH_LONG).show();
    }

    private void showLoading(boolean show) {
        swipeRefresh.setRefreshing(false);
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
