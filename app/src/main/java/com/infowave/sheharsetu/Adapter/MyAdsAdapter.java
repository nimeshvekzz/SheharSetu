package com.infowave.sheharsetu.Adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.infowave.sheharsetu.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the My Ads screen — vertical card list with 3 action buttons.
 * Reuses MyListingsAdapter.ListingItem for data.
 */
public class MyAdsAdapter extends RecyclerView.Adapter<MyAdsAdapter.ViewHolder> {

    public interface OnAdActionListener {
        void onAdClick(int listingId);

        void onMarkSoldClick(int listingId, boolean currentlySold);

        void onRepostClick(int listingId);

        void onEditClick(MyListingsAdapter.ListingItem item);

        void onDeleteClick(int listingId);
    }

    private final Context context;
    private final List<MyListingsAdapter.ListingItem> items = new ArrayList<>();
    private OnAdActionListener listener;

    public MyAdsAdapter(Context context) {
        this.context = context;
    }

    public void setOnAdActionListener(OnAdActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<MyListingsAdapter.ListingItem> listings) {
        items.clear();
        if (listings != null) {
            items.addAll(listings);
        }
        notifyDataSetChanged();
    }

    /** Remove an item by listing ID (after successful delete) */
    public void removeItem(int listingId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).listingId == listingId) {
                items.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    /** Update sold status for a single item */
    public void updateItemSoldStatus(int listingId, boolean isSold) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).listingId == listingId) {
                items.get(i).isSold = isSold;
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Move a reposted listing to top and mark as active */
    public void moveItemToTop(int listingId, String newPostedWhen, int repostCount) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).listingId == listingId) {
                MyListingsAdapter.ListingItem item = items.remove(i);
                item.isSold = false;
                item.postedWhen = newPostedWhen;
                item.repostCount = repostCount;
                items.add(0, item);
                notifyItemMoved(i, 0);
                notifyItemChanged(0);
                return;
            }
        }
    }

    public int getListCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_ad, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MyListingsAdapter.ListingItem item = items.get(position);

        // Load image
        if (!TextUtils.isEmpty(item.imageUrl)) {
            Glide.with(context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .error(R.drawable.ic_placeholder_circle)
                    .centerCrop()
                    .into(holder.imgListing);
        } else {
            holder.imgListing.setImageResource(R.drawable.ic_placeholder_circle);
        }

        // Text
        holder.tvTitle.setText(item.title);
        holder.tvPrice.setText(item.price);

        String categoryText = item.category;
        if (!TextUtils.isEmpty(item.subcategory)) {
            categoryText += " • " + item.subcategory;
        }
        holder.tvCategory.setText(categoryText);
        holder.tvPosted.setText(item.postedWhen);

        // Repost count
        if (item.repostCount > 0) {
            holder.tvRepostCount.setVisibility(View.VISIBLE);
            String countText = item.repostCount == 1
                    ? "Reposted 1 time"
                    : "Reposted " + item.repostCount + " times";
            holder.tvRepostCount.setText(countText);
        } else {
            holder.tvRepostCount.setVisibility(View.GONE);
        }

        // Status chip
        if (item.isSold) {
            holder.tvStatus.setText("SOLD");
            holder.tvStatus.setTextColor(0xFFFF5252);
            holder.tvStatus.setBackgroundColor(0x1AFF5252);
            holder.soldOverlay.setVisibility(View.VISIBLE);
            holder.btnMarkSold.setText("Available");
            holder.btnRepost.setAlpha(0.5f);
            holder.btnRepost.setEnabled(false);
        } else {
            holder.tvStatus.setText("ACTIVE");
            holder.tvStatus.setTextColor(0xFF4CAF50);
            holder.tvStatus.setBackgroundColor(0x1A4CAF50);
            holder.soldOverlay.setVisibility(View.GONE);
            holder.btnMarkSold.setText("Sold");
            holder.btnRepost.setAlpha(1.0f);
            holder.btnRepost.setEnabled(true);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onAdClick(item.listingId);
        });

        holder.btnMarkSold.setOnClickListener(v -> {
            if (listener != null)
                listener.onMarkSoldClick(item.listingId, item.isSold);
        });

        holder.btnRepost.setOnClickListener(v -> {
            if (listener != null)
                listener.onRepostClick(item.listingId);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null)
                listener.onEditClick(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null)
                listener.onDeleteClick(item.listingId);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgListing;
        FrameLayout soldOverlay;
        TextView tvTitle, tvPrice, tvCategory, tvPosted, tvStatus, tvRepostCount;
        MaterialButton btnMarkSold, btnRepost, btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgListing = itemView.findViewById(R.id.imgListing);
            soldOverlay = itemView.findViewById(R.id.soldOverlay);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvPosted = itemView.findViewById(R.id.tvPosted);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvRepostCount = itemView.findViewById(R.id.tvRepostCount);
            btnMarkSold = itemView.findViewById(R.id.btnMarkSold);
            btnRepost = itemView.findViewById(R.id.btnRepost);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
