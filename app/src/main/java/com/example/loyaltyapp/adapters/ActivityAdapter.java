package com.example.loyaltyapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.ActivityEvent;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.VH> {

    private final List<ActivityEvent> data;
    private final SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM • HH:mm", Locale.getDefault());

    public ActivityAdapter(List<ActivityEvent> data) {
        this.data = data;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ActivityEvent e = data.get(pos);

        // ---- Title
        String title;
        if ("scan".equals(e.type)) {
            title = "Scan" + (e.storeName != null && !e.storeName.isEmpty() ? " — " + e.storeName : "");
        } else if ("redemption".equals(e.type) || "redeem".equals(e.type)) {
            title = "Redemption";
        } else if ("bonus".equals(e.type)) {
            title = "Bonus";
        } else {
            title = "Activity";
        }
        h.activityTitle.setText(title);

        // ---- Date/time
        if (e.ts != null) {
            h.activityDateTime.setText(fmt.format(e.ts.toDate()));
        } else {
            h.activityDateTime.setText("—");
        }

        // ---- Points display
        int displayPts = e.points;
        // For redemption, ensure points are shown negative even if stored positive
        if (("redemption".equals(e.type) || "redeem".equals(e.type)) && displayPts > 0) {
            displayPts = -displayPts;
        }

        String sign = displayPts > 0 ? "+" : ""; // negatives already include '-'
        h.activityPoints.setText(sign + displayPts);

        // ---- Icon + background + text color
        if ("scan".equals(e.type)) {
            // Earned points
            h.activityIcon.setImageResource(R.drawable.ic_scan);
            h.iconBackground.setBackgroundResource(R.drawable.circle_background_earn); // green-ish bg
            h.activityPoints.setTextColor(0xFF4CAF50); // 🟩 green
        } else if ("redemption".equals(e.type) || "redeem".equals(e.type)) {
            // Redeemed points
            h.activityIcon.setImageResource(R.drawable.ic_gift);
            h.iconBackground.setBackgroundResource(R.drawable.circle_background_spend); // red-ish bg (if you have one)
            h.activityPoints.setTextColor(0xFFD32F2F); // 🔴 red
        } else if ("bonus".equals(e.type)) {
            // Bonus points
            h.activityIcon.setImageResource(R.drawable.ic_star);
            h.iconBackground.setBackgroundResource(R.drawable.circle_background_bonus); // yellow-ish bg
            h.activityPoints.setTextColor(0xFFFFC107); // 🟡 yellow
        } else {
            // Default
            h.activityIcon.setImageResource(R.drawable.ic_star);
            h.iconBackground.setBackgroundResource(R.drawable.circle_background_bonus);
            h.activityPoints.setTextColor(0xFFFFC107); // 🟡 yellow default
        }
    }


    @Override
    public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View iconBackground;
        ImageView activityIcon;
        TextView activityTitle, activityDateTime, activityDetails, activityPoints;

        VH(@NonNull View v) {
            super(v);
            iconBackground   = v.findViewById(R.id.iconBackground);
            activityIcon     = v.findViewById(R.id.activityIcon);
            activityTitle    = v.findViewById(R.id.activityTitle);
            activityDateTime = v.findViewById(R.id.activityDateTime);
            activityDetails  = v.findViewById(R.id.activityDetails);
            activityPoints   = v.findViewById(R.id.activityPoints);
        }
    }
}
