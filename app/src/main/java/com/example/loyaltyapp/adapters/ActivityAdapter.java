package com.example.loyaltyapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        com.example.loyaltyapp.databinding.ItemActivityBinding binding = com.example.loyaltyapp.databinding.ItemActivityBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ActivityEvent e = data.get(pos);

        // P1: bind against the canonical backend schema (type/delta/desc/refId).
        // ActivityEvent.fromDoc translates legacy aliases into the canonical type
        // values (earn|redeem|cancel|expire|birthday|adjust), so we branch on
        // those directly here.

        String suffix = e.desc != null && !e.desc.isEmpty() ? " — " + e.desc : "";
        String title;
        switch (e.type) {
            case ActivityEvent.TYPE_EARN:     title = "Scan" + suffix; break;
            case ActivityEvent.TYPE_REDEEM:   title = "Redemption" + suffix; break;
            case ActivityEvent.TYPE_CANCEL:   title = "Refund" + suffix; break;
            case ActivityEvent.TYPE_EXPIRE:   title = "Expired" + suffix; break;
            case ActivityEvent.TYPE_BIRTHDAY: title = "Birthday reward" + suffix; break;
            case ActivityEvent.TYPE_ADJUST:   title = e.desc != null && !e.desc.isEmpty() ? e.desc : "Adjustment"; break;
            default:                          title = "Activity"; break;
        }
        h.activityTitle.setText(title);

        if (e.ts != null) {
            h.activityDateTime.setText(fmt.format(e.ts.toDate()));
        } else {
            h.activityDateTime.setText("—");
        }

        // delta is already signed; just prepend '+' for positives.
        int displayPts = e.delta;
        String sign = displayPts > 0 ? "+" : "";
        h.activityPoints.setText(sign + displayPts);

        switch (e.type) {
            case ActivityEvent.TYPE_EARN:
                h.activityIcon.setImageResource(R.drawable.ic_scan);
                h.iconBackground.setBackgroundResource(R.drawable.circle_background_earn);
                h.activityPoints.setTextColor(color(h, R.color.status_success));
                break;
            case ActivityEvent.TYPE_REDEEM:
            case ActivityEvent.TYPE_EXPIRE:
                h.activityIcon.setImageResource(R.drawable.ic_gift);
                h.iconBackground.setBackgroundResource(R.drawable.circle_background_spend);
                h.activityPoints.setTextColor(color(h, R.color.status_error));
                break;
            case ActivityEvent.TYPE_CANCEL:
                // Refund: points come back, show as a credit.
                h.activityIcon.setImageResource(R.drawable.ic_gift);
                h.iconBackground.setBackgroundResource(R.drawable.circle_background_earn);
                h.activityPoints.setTextColor(color(h, R.color.status_success));
                break;
            default: // birthday, adjust, unknown
                h.activityIcon.setImageResource(R.drawable.ic_star);
                h.iconBackground.setBackgroundResource(R.drawable.circle_background_bonus);
                h.activityPoints.setTextColor(color(h, R.color.brand_gold));
                break;
        }
    }

    // Resolved per bind against the holder's context so the point colours follow
    // the active theme instead of being frozen at their light-theme values.
    private static int color(@NonNull VH h, @androidx.annotation.ColorRes int colorRes) {
        return ContextCompat.getColor(h.itemView.getContext(), colorRes);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        com.example.loyaltyapp.databinding.ItemActivityBinding binding;
        View iconBackground;
        ImageView activityIcon;
        TextView activityTitle, activityDateTime, activityDetails, activityPoints;

        VH(@NonNull com.example.loyaltyapp.databinding.ItemActivityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            iconBackground = binding.iconBackground;
            activityIcon = binding.activityIcon;
            activityTitle = binding.activityTitle;
            activityDateTime = binding.activityDateTime;
            activityDetails = binding.activityDetails;
            activityPoints = binding.activityPoints;
        }
    }
}
