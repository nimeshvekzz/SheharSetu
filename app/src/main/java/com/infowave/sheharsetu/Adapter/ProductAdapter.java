package com.infowave.sheharsetu.Adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.ProductDetail;
import com.infowave.sheharsetu.R;
import com.bumptech.glide.Glide;

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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> p = items.get(pos);

        // --- Image: prefer URL then resource ---
        String imageUrl = getString(p, "imageUrl", getString(p, "image_url", ""));
        int imgRes = getInt(p, "imageRes", 0);

        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(h.img).load(imageUrl).placeholder(placeholderIcon).error(placeholderIcon).into(h.img);
        } else if (imgRes != 0) {
            h.img.setImageResource(imgRes);
        } else {
            h.img.setImageResource(placeholderIcon);
        }

        // --- Text fields ---
        String title = getString(p, "title", "");
        String price = getString(p, "price", "");
        String city = getString(p, "city", "");

        h.title.setText(title);
        h.price.setText(price);
        h.city.setText(city);

        // Item click -> PDP
        h.itemView.setOnClickListener(v -> openPdp(title, price, city, imgRes));

        // Contact button
        h.btn.setOnClickListener(v -> {
            if (contactClickListener != null) {
                contactClickListener.onContactClick(p);
            } else {
                openPdp(title, price, city, imgRes);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /* ===================== ViewHolder ===================== */
    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView title, price, city;
        Button btn;

        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.img);
            title = v.findViewById(R.id.tvTitle);
            price = v.findViewById(R.id.tvPrice);
            city = v.findViewById(R.id.tvCity);
            btn = v.findViewById(R.id.btnContact);
        }
    }

    /* ===================== Helpers ===================== */
    private void openPdp(String title, String price, String city, int imgRes) {
        Intent i = new Intent(ctx, ProductDetail.class);
        i.putExtra("title", title);
        i.putExtra("price", price);
        i.putExtra("city", city);
        i.putExtra("posted", "2d ago");
        i.putExtra("desc", buildDescForPdp(title, price, city));
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
