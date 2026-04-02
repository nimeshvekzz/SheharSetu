package com.Anvexgroup.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.Anvexgroup.sheharsetu.R;

import java.util.ArrayList;
import java.util.List;

public class ContactSupportAdapter extends RecyclerView.Adapter<ContactSupportAdapter.VH> {

    public interface OnSupportClickListener {
        void onClick(@NonNull SupportItem item);
    }

    public static class SupportItem {
        public final String title;
        public final String value;
        public final String subtitle;
        public final String actionText;
        @DrawableRes public final int iconRes;

        public SupportItem(String title, String value, String subtitle, String actionText, int iconRes) {
            this.title = title;
            this.value = value;
            this.subtitle = subtitle;
            this.actionText = actionText;
            this.iconRes = iconRes;
        }
    }

    private final Context context;
    private final List<SupportItem> items = new ArrayList<>();
    private final OnSupportClickListener listener;

    public ContactSupportAdapter(@NonNull Context context,
                                 @NonNull List<SupportItem> data,
                                 @NonNull OnSupportClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.items.clear();
        this.items.addAll(data);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void replaceAll(@NonNull List<SupportItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_contact_support, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        SupportItem item = items.get(position);

        h.tvTitle.setText(item.title);
        h.tvValue.setText(item.value);
        h.tvSubtitle.setText(item.subtitle);
        h.tvAction.setText(item.actionText);
        h.ivIcon.setImageResource(item.iconRes);

        h.cardRoot.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        ImageView ivIcon;
        TextView tvTitle, tvValue, tvSubtitle, tvAction;

        VH(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardRoot);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvValue = itemView.findViewById(R.id.tvValue);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvAction = itemView.findViewById(R.id.tvAction);
        }
    }
}