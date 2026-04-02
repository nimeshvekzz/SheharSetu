package com.anvexgroup.sheharsetu.Adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.anvexgroup.sheharsetu.R;
// Enable Glide for loading subcategory icons from backend
import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Map;

public class SubFilterGridAdapter extends RecyclerView.Adapter<SubFilterGridAdapter.VH> {

    public interface OnSubClick { void onSubClick(Map<String, Object> sub); }

    private final List<Map<String, Object>> data;
    private final OnSubClick listener;

    @DrawableRes
    private final int placeholderIcon = R.drawable.ic_placeholder_circle;

    public SubFilterGridAdapter(List<Map<String, Object>> data, OnSubClick listener) {
        this.data = data;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subfilter_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> s = data.get(pos);

        int nameRes = getInt(s, "nameRes", 0);
        int iconRes = getInt(s, "iconRes", 0);
        String name    = getString(s, "name", "");       // dynamic
        String iconUrl = getString(s, "iconUrl", "");    // dynamic from backend

        if (!TextUtils.isEmpty(name)) {
            h.tv.setText(name);
        } else if (nameRes != 0) {
            h.tv.setText(nameRes);
        } else {
            h.tv.setText("");
        }

        if (iconRes != 0) {
            h.img.setImageResource(iconRes);
        } else if (!TextUtils.isEmpty(iconUrl)) {
            Glide.with(h.img.getContext())
                    .load(iconUrl)
                    .placeholder(placeholderIcon)
                    .error(placeholderIcon)
                    .into(h.img);
        } else {
            h.img.setImageResource(placeholderIcon);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSubClick(s);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card; ImageView img; TextView tv;
        VH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            img  = v.findViewById(R.id.imgSub);
            tv   = v.findViewById(R.id.tvSubName);
        }
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        if (m == null) return def;
        Object o = m.get(key);
        if (o instanceof Integer) return (Integer) o;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignore) {}
        return def;
    }
    private static String getString(Map<String, Object> m, String key, String def) {
        if (m == null) return def;
        Object o = m.get(key);
        if (o == null) return def;
        String s = String.valueOf(o);
        return TextUtils.isEmpty(s) ? def : s;
    }
}
