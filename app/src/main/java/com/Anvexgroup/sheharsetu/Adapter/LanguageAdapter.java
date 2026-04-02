package com.Anvexgroup.sheharsetu.Adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.Anvexgroup.sheharsetu.R;

import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.VH> {

    public interface OnLanguageClick {
        void onLanguageSelected(String[] lang);
    }

    private final List<String[]> languages;
    private final OnLanguageClick onLanguageClick;
    private int selectedPosition = RecyclerView.NO_POSITION;   // कोई default selection नहीं

    public LanguageAdapter(List<String[]> languages, OnLanguageClick onLanguageClick) {
        this.languages = languages;
        this.onLanguageClick = onLanguageClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_language, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String[] language = languages.get(position);

        holder.tvNativeName.setText(language[1]);   // native_name
        holder.tvEnglishName.setText(language[2]);  // english_name

        boolean isSelected = (position == selectedPosition);
        applySelectionStyle(holder, isSelected);

        holder.itemView.setOnClickListener(v -> {
            int oldPosition = selectedPosition;
            selectedPosition = position;

            if (oldPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(oldPosition);
            }
            notifyItemChanged(selectedPosition);

            if (onLanguageClick != null) {
                onLanguageClick.onLanguageSelected(language);
            }
        });
    }

    @Override
    public int getItemCount() {
        return languages.size();
    }

    /**
     * Selected / unselected visual style
     */
    private void applySelectionStyle(VH holder, boolean isSelected) {
        if (isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#111827")); // dark
            holder.cardView.setCardElevation(10f);
            holder.tvNativeName.setTextColor(Color.WHITE);
            holder.tvEnglishName.setTextColor(Color.parseColor("#E5E7EB")); // light gray
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.cardView.setCardElevation(5f);
            holder.tvNativeName.setTextColor(Color.parseColor("#111827")); // dark text
            holder.tvEnglishName.setTextColor(Color.parseColor("#6B7280")); // gray
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvNativeName;
        TextView tvEnglishName;
        CardView cardView;

        VH(@NonNull View itemView) {
            super(itemView);
            tvNativeName = itemView.findViewById(R.id.tvNativeName);
            tvEnglishName = itemView.findViewById(R.id.tvEnglishName);
            // root is CardView in item_language.xml
            cardView = (CardView) itemView;
        }
    }
}
