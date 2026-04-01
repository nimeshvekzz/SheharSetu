package com.infowave.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.infowave.sheharsetu.R;

import java.util.ArrayList;
import java.util.List;

public class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.VH> {

    public interface OnPick { void pick(Item c, int position); }

    private final List<Item> data = new ArrayList<>();
    private final OnPick onPick;
    private int selectedPos = RecyclerView.NO_POSITION;
    private String selectedId = null;

    public CategoryGridAdapter(List<Item> data, OnPick onPick) {
        if (data != null) this.data.addAll(data);
        this.onPick = onPick;
    }

    /** Replace the list and refresh. Keeps current selection by id if possible. */
    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<Item> list) {
        data.clear();
        if (list != null) data.addAll(list);
        // try to restore selection by id
        if (selectedId != null) {
            for (int i = 0; i < data.size(); i++) {
                if (selectedId.equals(data.get(i).id)) {
                    selectedPos = i;
                    break;
                }
            }
        } else {
            selectedPos = RecyclerView.NO_POSITION;
        }
        notifyDataSetChanged();
    }

    /** Programmatically select by position. */
    public void setSelectedPosition(int position) {
        if (position < 0 || position >= data.size()) return;
        int old = selectedPos;
        selectedPos = position;
        selectedId = data.get(position).id;
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        notifyItemChanged(selectedPos);
    }

    /** Programmatically select by id. */
    public void setSelectedId(String id) {
        selectedId = id;
        int old = selectedPos;
        selectedPos = RecyclerView.NO_POSITION;
        if (id != null) {
            for (int i = 0; i < data.size(); i++) {
                if (id.equals(data.get(i).id)) { selectedPos = i; break; }
            }
        }
        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
        if (selectedPos != RecyclerView.NO_POSITION) notifyItemChanged(selectedPos);
    }

    public Item getSelectedItem() {
        if (selectedPos >= 0 && selectedPos < data.size()) return data.get(selectedPos);
        return null;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item c = data.get(position);
        h.title.setText(c.name);

        // Use Glide to load the image from URL into the ImageView
        Glide.with(h.icon.getContext())
                .load(c.iconUrl) // Use the icon URL
                .placeholder(R.drawable.ic_placeholder_circle) // Placeholder image
                .into(h.icon);

        // selection visuals
        boolean isSelected = (position == selectedPos);
        @ColorInt int strokeColor = ContextCompat.getColor(h.card.getContext(), R.color.greenPrimary);
        h.card.setStrokeWidth(isSelected ? dp(h.card, 2) : 0);
        h.card.setStrokeColor(strokeColor);
        h.badge.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            int old = selectedPos;
            selectedPos = h.getBindingAdapterPosition();
            selectedId  = data.get(selectedPos).id;

            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selectedPos);

            if (onPick != null) onPick.pick(c, selectedPos);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final View badge; // top-right tick badge container (id: badge)

        VH(@NonNull View itemView) {
            super(itemView);
            card  = (MaterialCardView) itemView;
            icon  = itemView.findViewById(R.id.imgIcon);
            title = itemView.findViewById(R.id.tvName);
            badge = itemView.findViewById(R.id.badge); // <- must exist in your XML
        }
    }

    public static class Item {
        public final String id;
        public final String name;
        public final String iconUrl; // Use iconUrl instead of iconRes
        public final boolean requiresCondition;
        public Item(String id, String name, String iconUrl, boolean requiresCondition) {
            this.id = id; this.name = name; this.iconUrl = iconUrl; this.requiresCondition = requiresCondition;
        }
    }

    private static int dp(View v, int d) {
        return (int) (d * v.getResources().getDisplayMetrics().density);
    }
}
