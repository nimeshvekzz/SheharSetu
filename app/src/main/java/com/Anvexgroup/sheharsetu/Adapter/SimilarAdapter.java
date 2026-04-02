package com.Anvexgroup.sheharsetu.Adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.Anvexgroup.sheharsetu.ProductDetail;
import com.Anvexgroup.sheharsetu.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Similar Listings in ProductDetail.
 * Displays a horizontal list of related products with image, title, and price.
 */
public class SimilarAdapter extends RecyclerView.Adapter<SimilarAdapter.VH> {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private final Context context;

    public SimilarAdapter(Context context) {
        this.context = context;
    }

    /**
     * Update the adapter with new data
     */
    public void setItems(List<Map<String, Object>> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_similar, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> item = items.get(position);

        // Get data from map
        String title = getString(item, "title", "");
        String price = getString(item, "price", "");
        String imageUrl = getString(item, "image_url", "");
        String city = getString(item, "city", "Location");
        int listingId = getInt(item, "id", 0);

        // Bind text
        h.title.setText(title);
        h.price.setText(price);
        if (h.category != null) {
            h.category.setText(I18n.t(context, "LISTING"));
        }
        if (h.location != null) {
            h.location.setText(city);
        }

        // Load image with Glide
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_placeholder_circle)
                    .error(R.drawable.ic_placeholder_circle)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(h.image);
        } else {
            h.image.setImageResource(R.drawable.ic_placeholder_circle);
        }

        // Click to open ProductDetail
        h.itemView.setOnClickListener(v -> {
            if (listingId > 0) {
                Intent intent = new Intent(context, ProductDetail.class);
                intent.putExtra("listing_id", listingId);
                intent.putExtra("title", title);
                intent.putExtra("price", price);
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView price;
        final TextView category;
        final TextView location;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.simImage);
            title = itemView.findViewById(R.id.simTitle);
            price = itemView.findViewById(R.id.simPrice);
            category = itemView.findViewById(R.id.simCategory);
            location = itemView.findViewById(R.id.simLocation);
        }
    }

    /* ===================== Helpers ===================== */

    private static String getString(Map<String, Object> m, String key, String def) {
        if (m == null)
            return def;
        Object o = m.get(key);
        if (o == null)
            return def;
        String s = String.valueOf(o);
        return TextUtils.isEmpty(s) ? def : s;
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        if (m == null)
            return def;
        Object o = m.get(key);
        if (o instanceof Integer)
            return (Integer) o;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignore) {
        }
        return def;
    }
}
