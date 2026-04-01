package com.infowave.sheharsetu.Adapter;

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

import com.infowave.sheharsetu.R;

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
        
        // Premium Selection State: Scale + Color Highlights
        float targetScale = isSel ? 1.1f : 1.0f;
        h.itemView.animate().scaleX(targetScale).scaleY(targetScale).setDuration(250).start();
        
        if (isSel) {
            try {
                h.card.setStrokeColor(h.card.getContext().getColor(R.color.whatsapp_green));
                h.card.setStrokeWidth(5);
                h.title.setTextColor(h.title.getContext().getColor(R.color.whatsapp_green));
                h.title.setAlpha(1.0f);
            } catch (Exception e) {
                h.card.setStrokeColor(h.card.getContext().getColor(R.color.colorPrimary));
            }
        } else {
            h.card.setStrokeColor(h.card.getContext().getColor(R.color.premium_divider));
            h.card.setStrokeWidth(3);
            h.title.setTextColor(h.title.getContext().getColor(R.color.premium_text_main));
            h.title.setAlpha(0.7f);
        }

        h.icon.setAlpha(isSel ? 1.0f : 0.8f);

        h.itemView.setOnClickListener(v -> {
            if (selectedId != id) {
                int oldPos = -1;
                for (int i = 0; i < items.size(); i++) {
                    if (getInt(items.get(i), "id", -1) == selectedId) {
                        oldPos = i;
                        break;
                    }
                }
                selectedId = id;
                if (oldPos != -1) notifyItemChanged(oldPos);
                notifyItemChanged(position);
            }
            if (listener != null) listener.onClick(m);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView card;
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
