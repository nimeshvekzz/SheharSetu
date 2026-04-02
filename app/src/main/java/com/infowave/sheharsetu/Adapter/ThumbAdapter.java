package com.infowave.sheharsetu.Adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.infowave.sheharsetu.R;

import java.util.List;

public class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.VH> {

    public interface OnThumbClick { void onClick(int position); }

    private final Context ctx;
    private final List<Object> sources;   // String URL या Integer drawable
    private final OnThumbClick listener;
    private int selected = 0;

    public ThumbAdapter(Context ctx, List<Object> sources, OnThumbClick listener) {
        this.ctx = ctx;
        this.sources = sources;
        this.listener = listener;
    }

    public void setSelected(int pos) {
        selected = pos;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 64dp square thumbnail – programmatically create to avoid missing id issues
        int sizePx = (int) (parent.getResources().getDisplayMetrics().density * 64);
        ImageView iv = new ImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int pad = (int) (parent.getResources().getDisplayMetrics().density * 4);
        iv.setPadding(pad, pad, pad, pad);
        return new VH(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Object src = sources.get(position);

        // context को holder.itemView से लें; यह कभी null नहीं होगा
        Glide.with(h.itemView)
                .load(src)
                .placeholder(R.drawable.ic_placeholder_circle)
                .error(R.drawable.ic_placeholder_circle)
                .into(h.iv);

        // selection visual
        h.itemView.setAlpha(position == selected ? 1.0f : 0.7f);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(position);
            setSelected(position);
        });
    }

    @Override public int getItemCount() { return sources == null ? 0 : sources.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = (ImageView) itemView;
        }
    }
}
