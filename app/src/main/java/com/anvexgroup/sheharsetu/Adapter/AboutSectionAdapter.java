package com.anvexgroup.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

public class AboutSectionAdapter extends RecyclerView.Adapter<AboutSectionAdapter.VH> {

    public static class SectionItem {
        @DrawableRes public final int iconRes;
        public final String badge;
        public final String title;
        public final String subtitle;
        public final String description;
        public final String points;

        public SectionItem(@DrawableRes int iconRes,
                           String badge,
                           String title,
                           String subtitle,
                           String description,
                           String points) {
            this.iconRes = iconRes;
            this.badge = badge;
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.points = points;
        }
    }

    private final Context context;
    private final List<SectionItem> items = new ArrayList<>();

    public AboutSectionAdapter(@NonNull Context context, @NonNull List<SectionItem> data) {
        this.context = context;
        this.items.clear();
        this.items.addAll(data);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void replaceAll(@NonNull List<SectionItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_about_section, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SectionItem item = items.get(position);

        h.ivIcon.setImageResource(item.iconRes);
        h.tvTitle.setText(item.title);

        setTextOrHide(h.tvBadge, item.badge);
        setTextOrHide(h.tvSubtitle, item.subtitle);
        setTextOrHide(h.tvDescription, item.description);
        setTextOrHide(h.tvPoints, item.points);

        h.tvPoints.setVisibility(TextUtils.isEmpty(item.points) ? View.GONE : View.VISIBLE);
        h.itemView.findViewById(R.id.pointsContainer)
                .setVisibility(TextUtils.isEmpty(item.points) ? View.GONE : View.VISIBLE);
    }

    private void setTextOrHide(TextView tv, String value) {
        if (TextUtils.isEmpty(value)) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(value);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivIcon;
        TextView tvBadge, tvTitle, tvSubtitle, tvDescription, tvPoints;

        VH(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvBadge = itemView.findViewById(R.id.tvBadge);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPoints = itemView.findViewById(R.id.tvPoints);
        }
    }
}