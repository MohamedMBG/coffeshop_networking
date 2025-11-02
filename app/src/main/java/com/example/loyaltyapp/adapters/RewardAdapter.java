package com.example.loyaltyapp.adapters;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.Rewards;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.Locale;
import java.util.function.IntSupplier;

public class RewardAdapter extends ListAdapter<Rewards, RewardAdapter.VH> {

    public interface OnRedeem {
        void onClick(@NonNull Rewards r);
    }

    private final IntSupplier pointsSupplier;
    private final OnRedeem onRedeem;

    public RewardAdapter(IntSupplier pointsSupplier, OnRedeem onRedeem) {
        super(DIFF);
        this.pointsSupplier = pointsSupplier;
        this.onRedeem = onRedeem;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vType) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_reward, p, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Rewards r = getItem(pos);
        int userPts = pointsSupplier.getAsInt();

        h.tvTitle.setText(r.name);
        h.tvPrice.setText(String.format(Locale.getDefault(), "%.0f MAD", r.priceMAD));
        h.tvCost.setText(r.redeemPoints + " pts");
        h.tvDesc.setVisibility(View.GONE);

        Glide.with(h.itemView.getContext())
                .load(r.imagePath) // if this is a Storage path, switch to StorageReference
                .placeholder(R.drawable.placeholder_coffee)
                .into(h.imgThumb);

        boolean canRedeem = userPts >= r.redeemPoints;
        h.btnRedeem.setEnabled(canRedeem);
        h.btnRedeem.setAlpha(canRedeem ? 1f : 0.5f);
        h.btnRedeem.setOnClickListener(view -> onRedeem.onClick(r));
    }

    static final DiffUtil.ItemCallback<Rewards> DIFF = new DiffUtil.ItemCallback<Rewards>() {
        @Override public boolean areItemsTheSame(@NonNull Rewards a, @NonNull Rewards b) { return equal(a.id, b.id); }
        @Override public boolean areContentsTheSame(@NonNull Rewards a, @NonNull Rewards b) {
            return a.active == b.active
                    && a.redeemPoints == b.redeemPoints
                    && equal(a.name, b.name)
                    && equal(a.imagePath, b.imagePath)
                    && a.priceMAD == b.priceMAD;
        }
        private boolean equal(Object x, Object y) { return x == y || (x != null && x.equals(y)); }
    };

    static class VH extends RecyclerView.ViewHolder {
        ShapeableImageView imgThumb;
        TextView tvTitle, tvDesc, tvPrice, tvCost;
        MaterialButton btnRedeem;
        VH(@NonNull View v) {
            super(v);
            imgThumb = v.findViewById(R.id.imgThumb);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvDesc = v.findViewById(R.id.tvDesc);
            tvPrice = v.findViewById(R.id.tvPrice);
            tvCost = v.findViewById(R.id.tvCost);
            btnRedeem = v.findViewById(R.id.btnRedeem);
        }
    }
}
