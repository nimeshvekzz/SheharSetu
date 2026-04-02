package com.Anvexgroup.sheharsetu.Adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.Anvexgroup.sheharsetu.R;

// Enable Glide for loading network images
import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {

    public interface OnCategoryClick { void onClick(Map<String, Object> cat); }

    private final List<Map<String, Object>> items;
    private final OnCategoryClick listener;
    private int selectedId = -1;

    @DrawableRes
    private final int placeholderIcon = R.drawable.ic_placeholder_circle;

    public CategoryAdapter(List<Map<String, Object>> items, OnCategoryClick listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectedId(int id) {
        selectedId = id;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Map<String, Object> m = items.get(position);

        int id      = getInt(m, "id", -1);
        int iconRes = getInt(m, "iconRes", 0);
        int nameRes = getInt(m, "nameRes", 0);
        String nameTxt = getString(m, "name", "");         // dynamic title
        String iconUrl = getString(m, "iconUrl", "");      // dynamic icon from backend

        // ---- Title: prefer string; fallback to nameRes ----
        if (!TextUtils.isEmpty(nameTxt)) {
            h.title.setText(nameTxt);
        } else if (nameRes != 0) {
            h.title.setText(nameRes);
        } else {
            h.title.setText("");
        }

        // ---- Icon: prefer iconRes; else iconUrl (Glide); else placeholder ----
        if (iconRes != 0) {
            // Static drawable from resources
            h.icon.setImageResource(iconRes);
        } else if (!TextUtils.isEmpty(iconUrl)) {
            // Load from network using Glide (full URL coming from list_categories.php)
            Glide.with(h.icon.getContext())
                    .load(iconUrl)
                    .placeholder(placeholderIcon)
                    .error(placeholderIcon)
                    .into(h.icon);
        } else {
            h.icon.setImageResource(placeholderIcon);
        }

        boolean isSel = (id == selectedId);
        h.itemView.setSelected(isSel);
        h.card.setSelected(isSel);
        h.title.setSelected(isSel);
        h.icon.setAlpha(isSel ? 1.0f : 0.85f);

        h.itemView.setOnClickListener(v -> {
            if (selectedId != id) {
                selectedId = id;
                notifyDataSetChanged();
            }
            if (listener != null) listener.onClick(m);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        ImageView icon;
        TextView title;
        VH(@NonNull View v) {
            super(v);
            card  = v.findViewById(R.id.catCard);
            icon  = v.findViewById(R.id.catIcon);
            title = v.findViewById(R.id.catTitle);
        }
    }

    // -------- helpers (safe casting) --------
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
