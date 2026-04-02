package com.anvexgroup.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.anvexgroup.sheharsetu.ProductDetail;
import com.anvexgroup.sheharsetu.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private final Context ctx;

    public interface OnContactClickListener {
        void onContactClick(@NonNull Map<String, Object> product);
    }

    private OnContactClickListener contactClickListener;

    @DrawableRes
    private final int placeholderIcon = R.drawable.ic_placeholder_circle;

    public ProductAdapter(Context ctx) {
        this.ctx = ctx;
    }



    public void setOnContactClickListener(OnContactClickListener l) {
        this.contactClickListener = l;
    }

    public void setItems(List<Map<String, Object>> list) {
        items.clear();
        if (list != null)
            items.addAll(list);
        notifyDataSetChanged();
    }

    /** Append items for infinite scroll pagination */
    public void addItems(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty())
            return;
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> p = items.get(pos);

        // --- Text fields ---
        String title = getString(p, "title", "");
        String price = getString(p, "price", "");
        String city = getString(p, "city", "");
        int id = getInt(p, "id", 0);
        if (id == 0)
            id = getInt(p, "listing_id", 0);

        h.title.setText(title);

        // Format price with ₹ prefix
        String displayPrice;
        if (TextUtils.isEmpty(price) || price.equals("null") || price.equals("0")) {
            displayPrice = I18n.t(ctx, "Price on Request");
        } else {
            // Already has ₹? show as-is, else add prefix
            displayPrice = price.startsWith("₹") ? price : "₹ " + price;
        }
        h.price.setText(displayPrice);
        
        String displayCity = TextUtils.isEmpty(city) ? "" : "📍 " + city;
        String distance = getString(p, "distance", "");
        if (!TextUtils.isEmpty(distance) && !distance.equals("0") && !distance.equals("null")) {
            try {
                double distVal = Double.parseDouble(distance);
                displayCity += " • " + String.format(java.util.Locale.getDefault(), "%.1f km", distVal);
            } catch (Exception e) {
                // Fallback if parsing fails but string is present
                displayCity += " • " + distance + " km";
            }
        }
        String posted = getString(p, "posted_when", getString(p, "posted_time", ""));
        
        // --- Posted Time ---
        if (h.posted != null) {
            h.posted.setText(posted);
        }

        String status = getString(p, "status", "active");
        boolean isSold = "sold".equalsIgnoreCase(status);
        if (h.soldBadge != null) {
            if (isSold) {
                h.soldBadge.setText(I18n.t(ctx, "SOLD"));
                h.soldBadge.setTextColor(ctx.getResources().getColor(android.R.color.white));
                h.soldBadge.setBackgroundResource(R.drawable.bg_sold_badge);
                h.soldBadge.setVisibility(View.VISIBLE);
            } else {
                // If not sold, show "New" as per mockup style
                h.soldBadge.setText(I18n.t(ctx, "New"));
                h.soldBadge.setTextColor(android.graphics.Color.parseColor("#27AE60"));
                h.soldBadge.setBackgroundResource(R.drawable.bg_new_badge);
                h.soldBadge.setVisibility(View.VISIBLE);
            }
        }

        int finalId = id;
        h.itemView.setOnClickListener(v -> openPdp(finalId, title, price, city, posted, 0));

        // --- Load image using Glide ---
        List<String> images = (List<String>) p.get("images");
        String imageUrl = "";
        if (images != null && !images.isEmpty()) {
            imageUrl = images.get(0);
        }
        if (TextUtils.isEmpty(imageUrl)) {
            imageUrl = getString(p, "imageUrl", getString(p, "image_url", ""));
        }

        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(h.productImage.getContext())
                    .load(imageUrl)
                    .placeholder(placeholderIcon)
                    .error(placeholderIcon)
                    .centerCrop()
                    .into(h.productImage);
        } else {
            h.productImage.setImageResource(placeholderIcon);
        }

        // Contact button (might be hidden in grid view)
        if (h.btn != null) {
            h.btn.setOnClickListener(v -> {
                if (contactClickListener != null) {
                    contactClickListener.onContactClick(p);
                } else {
                    openPdp(finalId, title, price, city, posted, 0);
                }
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.cleanup();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /* ===================== ViewHolder ===================== */
    static class VH extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView title, price, city, soldBadge, posted;
        View btn, btnFavorite;

        VH(@NonNull View v) {
            super(v);
            productImage = v.findViewById(R.id.productImage);
            title = v.findViewById(R.id.tvTitle);
            price = v.findViewById(R.id.tvPrice);
            city = v.findViewById(R.id.tvCity);
            btn = v.findViewById(R.id.btnContact);
//            soldBadge = v.findViewById(R.id.tvSoldBadge);
            posted = v.findViewById(R.id.tvPosted);
//            btnFavorite = v.findViewById(R.id.btnFavorite);
        }

        void cleanup() {
            // No-op: nothing to clean up with simple ImageView
        }
    }

    /* ===================== Helpers ===================== */
    private void openPdp(int id, String title, String price, String city, String posted, int imgRes) {
        Intent i = new Intent(ctx, ProductDetail.class);
        i.putExtra("listing_id", id);
        i.putExtra("title", title);
        i.putExtra("price", price);
        i.putExtra("city", city);
        i.putExtra("posted", TextUtils.isEmpty(posted) ? "" : posted);
        i.putExtra("desc", buildDescForPdp(title, price, city));
        // We no longer pass resource IDs for main flow, but keeping compatible for now
        i.putExtra("images", new int[] { imgRes != 0 ? imgRes : placeholderIcon });
        ctx.startActivity(i);
    }

    private static String buildDescForPdp(String title, String price, String city) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(title))
            sb.append(title);
        if (!TextUtils.isEmpty(price))
            sb.append(" • ").append(price);
        if (!TextUtils.isEmpty(city))
            sb.append(" • ").append(city);
        return sb.toString();
    }

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
