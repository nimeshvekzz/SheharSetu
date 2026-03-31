package com.infowave.sheharsetu.Adapter;

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

public class SubcategoryGridAdapter extends RecyclerView.Adapter<SubcategoryGridAdapter.VH> {

    public interface OnPick { void pick(Item s, int position); }

    private final List<Item> data = new ArrayList<>();
    private final OnPick onPick;

    private int selected = RecyclerView.NO_POSITION;
    private String selectedId = null;

    public SubcategoryGridAdapter(List<Item> init, OnPick onPick) {
        if (init != null) data.addAll(init);
        this.onPick = onPick;
    }

    /** Replace items and keep selection by id if possible. */
    public void submit(List<Item> items) {
        data.clear();
        if (items != null) data.addAll(items);

        if (selectedId != null) {
            selected = RecyclerView.NO_POSITION;
            for (int i = 0; i < data.size(); i++) {
                if (selectedId.equals(data.get(i).id)) { selected = i; break; }
            }
        } else selected = RecyclerView.NO_POSITION;

        notifyDataSetChanged();
    }

//    /** Programmatically select by id. */
//    public void setSelectedId(String id) {
//        selectedId = id;
//        int old = selected;
//        selected = RecyclerView.NO_POSITION;
//        if (id != null) {
//            for (int i = 0; i < data.size(); i++) {
//                if (id.equals(data.get(i).id)) { selected = i; break; }
//            }
//        }
//        if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
//        if (selected != RecyclerView.NO_POSITION) notifyItemChanged(selected);
//    }


    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subcategory_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Item s = data.get(position);
        h.title.setText(s.name);

        // Use Glide to load the image from URL into the ImageView
        Glide.with(h.icon.getContext())
                .load(s.iconUrl) // Use the icon URL
                .placeholder(R.drawable.ic_placeholder_circle) // Placeholder image
                .into(h.icon);

        boolean isSel = position == selected;
        @ColorInt int stroke = ContextCompat.getColor(h.card.getContext(), R.color.greenPrimary);
        h.card.setStrokeWidth(isSel ? dp(h.card, 2) : 0);
        h.card.setStrokeColor(stroke);
        h.badge.setVisibility(isSel ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = h.getBindingAdapterPosition();
            selectedId = data.get(selected).id;

            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(selected);

            if (onPick != null) onPick.pick(s, selected);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView icon;
        final TextView title;
        final View badge;
        VH(@NonNull View itemView) {
            super(itemView);
            card  = (MaterialCardView) itemView;
            icon  = itemView.findViewById(R.id.imgIcon);
            title = itemView.findViewById(R.id.tvName);
            badge = itemView.findViewById(R.id.badge);
        }
    }

    public static class Item {
        public final String id;
        public final String parentId;
        public final String name;
        public final String iconUrl; // Use iconUrl instead of iconRes
        public final Boolean requiresCondition;
        public Item(String id, String parentId, String name, String iconUrl, Boolean requiresCondition) {
            this.id = id; this.parentId = parentId; this.name = name; this.iconUrl = iconUrl; this.requiresCondition = requiresCondition;
        }
    }

    private static int dp(View v, int d) {
        return (int) (d * v.getResources().getDisplayMetrics().density);
    }
}
