package com.example.loyaltyapp.fragments;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.loyaltyapp.LoyaltyActivity;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.adapters.ActivityAdapter;
import com.example.loyaltyapp.models.ActivityEvent;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class ActivityFragment extends Fragment {

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
    private final List<ActivityEvent> allEvents = new ArrayList<>();
    private final List<ActivityEvent> shownEvents = new ArrayList<>();
    private ActivityAdapter adapter;

    // Filters
    private String typeFilter = "all";   // all|scan|redemption|bonus
    private Date fromDate = null, toDate = null; // inclusive ranges for custom/week/month

    // Firebase
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_activity, container, false);

        // Header
        tvCurrentPoints = v.findViewById(R.id.tvCurrentPoints);
        tvTotalVisits   = v.findViewById(R.id.tvTotalVisits);
        tvLastScan      = v.findViewById(R.id.tvLastScan);
        tvUserLevel     = v.findViewById(R.id.tvUserLevel);
        btnInfo         = v.findViewById(R.id.btnInfo);

        // List/empty/loading
        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        recycler     = v.findViewById(R.id.activityRecyclerView);
        emptyState   = v.findViewById(R.id.emptyState);
        loading      = v.findViewById(R.id.loadingIndicator);
        btnScanNow   = v.findViewById(R.id.btnScanNow);

        // Chips
        chipGroupType   = v.findViewById(R.id.chipGroupType);
        chipGroupDate   = v.findViewById(R.id.chipGroupDate);
        chipAll         = v.findViewById(R.id.chipAll);
        chipScans       = v.findViewById(R.id.chipScans);
        chipRedemptions = v.findViewById(R.id.chipRedemptions);
        chipBonuses     = v.findViewById(R.id.chipBonuses);
        chipThisWeek    = v.findViewById(R.id.chipThisWeek);
        chipThisMonth   = v.findViewById(R.id.chipThisMonth);
        chipCustomDate  = v.findViewById(R.id.chipCustomDate);

        // Recycler
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ActivityAdapter(shownEvents);
        recycler.setAdapter(adapter);

        // Pull-to-refresh
        swipeRefresh.setOnRefreshListener(this::refresh);

        // Chip listeners
        chipGroupType.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return; // selectionRequired=true in XML, but just in case
            int id = ids.get(0);
            if (id == chipAll.getId()) typeFilter = "all";
            else if (id == chipScans.getId()) typeFilter = "scan";
            else if (id == chipRedemptions.getId()) typeFilter = "redemption";
            else if (id == chipBonuses.getId()) typeFilter = "bonus";
            applyFilters();
        });

        chipGroupDate.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) { // no date filter
                fromDate = toDate = null;
                applyFilters();
                return;
            }
            int id = ids.get(0);
            if (id == chipThisWeek.getId()) {
                setThisWeekRange();
            } else if (id == chipThisMonth.getId()) {
                setThisMonthRange();
            } else if (id == chipCustomDate.getId()) {
                openCustomRangePicker();
            }
            applyFilters();
        });

        // Info click: tiny toast/help
        btnInfo.setOnClickListener(vw ->
                Toast.makeText(requireContext(), "Earn points by scanning in-store. Redeem rewards from your points.", Toast.LENGTH_SHORT).show()
        );

        // Scan now → switch to Scan tab
        btnScanNow.setOnClickListener(vw -> {
            if (getActivity() instanceof LoyaltyActivity) {
                ((LoyaltyActivity) getActivity()).openScanTab(); // add small helper in the activity (below)
            }
        });

        // First load
        startLoading(true);
        loadHeaderAndList();

        return v;
    }

    private void refresh() {
        loadHeaderAndList();
    }

    /** Loads header counters + list in parallel. */
    private void loadHeaderAndList() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (TextUtils.isEmpty(uid)) {
            stopLoading();
            return;
        }

        // Header counters
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    int points = doc.contains("points") ? safeInt(doc.getLong("points")) : 0;
                    int visits = doc.contains("visits") ? safeInt(doc.getLong("visits")) : 0;
                    tvCurrentPoints.setText(String.valueOf(points));
                    tvTotalVisits.setText(String.valueOf(visits));
                    tvUserLevel.setText(levelFor(points));
                });

        // Activities list
        db.collection("users").document(uid)
                .collection("activities")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(200)
                .get()
                .addOnSuccessListener(snap -> {
                    allEvents.clear();
                    Timestamp lastTs = null;
                    for (DocumentSnapshot d : snap) {
                        ActivityEvent ev = ActivityEvent.fromDoc(d);
                        if (ev != null) {
                            allEvents.add(ev);
                            if (lastTs == null) lastTs = ev.ts;
                        }
                    }
                    // Last scan text
                    if (lastTs != null) {
                        tvLastScan.setText(relativeTime(lastTs.toDate()));
                    } else {
                        tvLastScan.setText("—");
                    }

                    applyFilters();
                    stopLoading();
                })
                .addOnFailureListener(e -> {
                    stopLoading();
                    Toast.makeText(requireContext(), "Failed to load activity: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    /** Applies type + date filters in-memory (fast for <=200 items). */
    private void applyFilters() {
        shownEvents.clear();
        for (ActivityEvent ev : allEvents) {
            if (!typePass(ev)) continue;
            if (!datePass(ev)) continue;
            shownEvents.add(ev);
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(shownEvents.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean typePass(ActivityEvent ev) {
        if ("all".equals(typeFilter)) return true;
        return typeFilter.equals(ev.type);
    }

    private boolean datePass(ActivityEvent ev) {
        if (fromDate == null && toDate == null) return true;
        Date t = ev.ts != null ? ev.ts.toDate() : null;
        if (t == null) return false;
        if (fromDate != null && t.before(trimStart(fromDate))) return false;
        if (toDate != null && t.after(trimEnd(toDate))) return false;
        return true;
    }

    private void setThisWeekRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        fromDate = c.getTime();
        c.add(Calendar.DAY_OF_YEAR, 6);
        toDate = c.getTime();
    }

    private void setThisMonthRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_MONTH, 1);
        fromDate = c.getTime();
        c.add(Calendar.MONTH, 1);
        c.add(Calendar.DAY_OF_MONTH, -1);
        toDate = c.getTime();
    }

    private void openCustomRangePicker() {
        // simple two pickers (start then end); for production use a proper date-range picker
        final Calendar start = Calendar.getInstance();
        final Calendar end   = Calendar.getInstance();

        DatePickerDialog dpStart = new DatePickerDialog(requireContext(), (v, y, m, d) -> {
            start.set(y, m, d, 0, 0, 0);
            DatePickerDialog dpEnd = new DatePickerDialog(requireContext(), (v2, y2, m2, d2) -> {
                end.set(y2, m2, d2, 23, 59, 59);
                fromDate = start.getTime();
                toDate   = end.getTime();
                applyFilters();
            }, end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH));
            dpEnd.show();
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
        dpStart.show();
    }

    private void startLoading(boolean first) {
        if (first) loading.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(true);
        emptyState.setVisibility(View.GONE);
    }

    private void stopLoading() {
        loading.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
    }

    private static int safeInt(Long n) {
        return n == null ? 0 : n.intValue();
    }

    private static String levelFor(int points) {
        if (points >= 200) return "Gold";
        if (points >= 100) return "Silver";
        return "Bronze";
    }

    private static Date trimStart(Date d) {
        Calendar c = Calendar.getInstance(); c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static Date trimEnd(Date d) {
        Calendar c = Calendar.getInstance(); c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
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
}
