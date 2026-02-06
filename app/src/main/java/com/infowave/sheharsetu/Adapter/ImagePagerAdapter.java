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

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.VH> {
    public interface OnItemClickListener {
        void onClick(int position);
    }

    private final Context ctx;
    private final List<Object> sources;
    private final OnItemClickListener listener;

    public ImagePagerAdapter(Context ctx, List<Object> sources, OnItemClickListener listener) {
        this.ctx = ctx;
        this.sources = sources;
        this.listener = listener;
    }

    public ImagePagerAdapter(Context ctx, List<Object> sources) {
        this(ctx, sources, null);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new VH(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Object src = sources.get(position);
        Glide.with(h.iv)
                .load(src)
                .placeholder(R.drawable.ic_placeholder_circle)
                .error(R.drawable.ic_placeholder_circle)
                .into(h.iv);

        h.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onClick(position);
        });
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;

        VH(@NonNull View itemView) {
            super(itemView);
            iv = (ImageView) itemView;
        }
    }
}
