package com.infowave.sheharsetu.Adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.R;
import com.infowave.sheharsetu.model.NotificationModel;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<NotificationModel> notifications;
    private final Context context;

    public NotificationAdapter(Context context, List<NotificationModel> notifications) {
        this.context = context;
        this.notifications = notifications;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel item = notifications.get(position);

        holder.tvTitle.setText(I18n.t(context, item.getTitle()));
        holder.tvContent.setText(I18n.t(context, item.getContent()));
        holder.tvTime.setText(I18n.t(context, item.getTimestamp()));

        // Icon based on type
        int iconRes = R.drawable.ic_bell_vector;
        int colorTint = R.color.colorPrimary;
        int bgColor = 0xFFE0F2F1; // Default light teal

        if ("SUCCESS".equalsIgnoreCase(item.getType())) {
            iconRes = R.drawable.ic_check_circle_24;
            colorTint = R.color.accentGreen;
            bgColor = 0xFFE8F5E9; // Light green
        } else if ("WARNING".equalsIgnoreCase(item.getType())) {
            iconRes = R.drawable.ic_help_24;
            colorTint = R.color.colorError;
            bgColor = 0xFFFFEBEE; // Light red
        } else if ("INFO".equalsIgnoreCase(item.getType())) {
            iconRes = R.drawable.ic_info_outline;
            colorTint = R.color.colorPrimary;
            bgColor = 0xFFE3F2FD; // Light blue
        }

        // Fix: Use setBackgroundTintList to keep the circular shape of the background drawable
        View iconContainer = holder.itemView.findViewById(R.id.layoutIconContainer);
        if (iconContainer != null) {
            androidx.core.view.ViewCompat.setBackgroundTintList(iconContainer, 
                android.content.res.ColorStateList.valueOf(bgColor));
        }

        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setColorFilter(ContextCompat.getColor(context, colorTint));

        // Unread feedback: subtle tint for the whole card
        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemView;
        if (!item.isRead()) {
            card.setCardBackgroundColor(Color.parseColor("#F5FEFD")); // Very subtle premium teal tint
            card.setStrokeColor(ContextCompat.getColor(context, R.color.colorPrimary));
            card.setStrokeWidth(3); // Slightly thicker for unread
            holder.viewUnread.setVisibility(View.VISIBLE);
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white));
            card.setStrokeColor(ContextCompat.getColor(context, R.color.colorBorder));
            card.setStrokeWidth(2);
            holder.viewUnread.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvContent, tvTime;
        View viewUnread;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvContent = itemView.findViewById(R.id.tvNotificationContent);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            viewUnread = itemView.findViewById(R.id.viewUnreadDot);
        }
    }
}
