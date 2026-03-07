package com.example.loyaltyapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.MenuItemModel;

import java.util.List;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.VH> {

    public interface OnItemClick {
        void onClick(MenuItemModel item);
    }

    private List<MenuItemModel> data;
    private final OnItemClick onItemClick;

    public MenuAdapter(List<MenuItemModel> data, OnItemClick onItemClick) {
        this.data = data;
        this.onItemClick = onItemClick;
    }

    public void submit(List<MenuItemModel> items) {
        this.data = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        com.example.loyaltyapp.databinding.MenuItemBinding binding = com.example.loyaltyapp.databinding.MenuItemBinding
                .inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        MenuItemModel m = data.get(pos);
        h.title.setText(m.getName());
        h.price.setText(m.getPriceMAD() == null ? "" : (m.getPriceMAD().intValue() + " MAD"));

        Glide.with(h.image.getContext())
                .load(m.getImageUrl())
                .into(h.image);

        h.itemView.setOnClickListener(v -> {
            if (onItemClick != null)
                onItemClick.onClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        com.example.loyaltyapp.databinding.MenuItemBinding binding;
        ImageView image;
        TextView title, price;

        VH(@NonNull com.example.loyaltyapp.databinding.MenuItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            image = binding.itemImage;
            title = binding.itemTitle;
            price = binding.itemPrice;
        }
    }
}
