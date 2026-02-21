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
 * Adapter for displaying user's own listings in Profile screen
 */
public class MyListingsAdapter extends RecyclerView.Adapter<MyListingsAdapter.ViewHolder> {

    public interface OnListingActionListener {
        void onListingClick(int listingId);

        void onMarkSoldClick(int listingId, boolean currentlySold);
    }

    private final Context context;
    private final List<ListingItem> items = new ArrayList<>();
    private OnListingActionListener listener;

    public MyListingsAdapter(Context context) {
        this.context = context;
    }

    public void setOnListingActionListener(OnListingActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ListingItem> listings) {
        items.clear();
        if (listings != null) {
            items.addAll(listings);
        }
        notifyDataSetChanged();
    }

    public void updateItemSoldStatus(int listingId, boolean isSold) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).listingId == listingId) {
                items.get(i).isSold = isSold;
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_listing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ListingItem item = items.get(position);

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

        // Set text
        holder.tvTitle.setText(item.title);
        holder.tvPrice.setText(item.price);

        // Category + subcategory
        String categoryText = item.category;
        if (!TextUtils.isEmpty(item.subcategory)) {
            categoryText += " • " + item.subcategory;
        }
        holder.tvCategory.setText(categoryText);
        holder.tvPosted.setText(item.postedWhen);

        // Handle sold status
        if (item.isSold) {
            holder.soldOverlay.setVisibility(View.VISIBLE);
            holder.btnMarkSold.setText("Mark Available");
            holder.btnMarkSold.setAlpha(0.7f);
        } else {
            holder.soldOverlay.setVisibility(View.GONE);
            holder.btnMarkSold.setText("Mark Sold");
            holder.btnMarkSold.setAlpha(1.0f);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onListingClick(item.listingId);
            }
        });

        holder.btnMarkSold.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMarkSoldClick(item.listingId, item.isSold);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgListing;
        FrameLayout soldOverlay;
        TextView tvTitle, tvPrice, tvCategory, tvPosted;
        MaterialButton btnMarkSold;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgListing = itemView.findViewById(R.id.imgListing);
            soldOverlay = itemView.findViewById(R.id.soldOverlay);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvPosted = itemView.findViewById(R.id.tvPosted);
            btnMarkSold = itemView.findViewById(R.id.btnMarkSold);
        }
    }

    /**
     * Data class for listing items
     */
    public static class ListingItem {
        public int listingId;
        public int categoryId;
        public int subcategoryId;
        public String title;
        public String price;
        public String city;
        public String category;
        public String subcategory;
        public String imageUrl;
        public boolean isSold;
        public String postedWhen;
        public int repostCount;

        public ListingItem() {
        }

        public static ListingItem fromJson(org.json.JSONObject json) {
            ListingItem item = new ListingItem();
            item.listingId = json.optInt("listing_id", 0);
            item.categoryId = json.optInt("category_id", 0);
            item.subcategoryId = json.optInt("subcategory_id", 0);
            item.title = json.optString("title", "");
            item.price = json.optString("price", "");
            item.city = json.optString("city", "");
            item.category = json.optString("category", "");
            item.subcategory = json.optString("subcategory", "");
            item.imageUrl = json.optString("image_url", "");
            item.isSold = json.optBoolean("is_sold", false);
            item.postedWhen = json.optString("posted_when", "");
            item.repostCount = json.optInt("repost_count", 0);
            return item;
        }
    }
}
