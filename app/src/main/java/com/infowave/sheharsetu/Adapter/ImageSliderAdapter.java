package com.infowave.sheharsetu.Adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.infowave.sheharsetu.R;

import java.util.List;

public class ImageSliderAdapter extends RecyclerView.Adapter<ImageSliderAdapter.SliderVH> {

    private final List<String> imageUrls;
    private final View.OnClickListener onItemClick;

    public ImageSliderAdapter(List<String> imageUrls, View.OnClickListener onItemClick) {
        this.imageUrls = imageUrls;
        this.onItemClick = onItemClick;
    }

    @NonNull
    @Override
    public SliderVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // We can reuse a simple layout or inflate item_product_image.xml
        // For simplicity, let's create a dynamic ImageView or use a simple layout file
        // if exists.
        // Since we need to modify XMLs anyway, let's assume we inflate a simple
        // ImageView
        // But better to use a layout file. Let's create a simple layout implicitly or
        // using existing resources.
        // Actually, let's create a layout item_slider_image.xml first?
        // No, let's just create an ImageView programmatically to avoid creating too
        // many files if possible,
        // OR inflate a layout that contains just an ImageView.

        // Let's standard usage: inflate a new layout.
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slider_image, parent, false);
        return new SliderVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SliderVH holder, int position) {
        String url = imageUrls.get(position);

        Glide.with(holder.itemView.getContext())
                .load(url)
                .placeholder(R.drawable.ic_placeholder_circle)
                .error(R.drawable.ic_placeholder_circle)
                .centerCrop()
                .into(holder.img);

        holder.itemView.setOnClickListener(v -> {
            if (onItemClick != null)
                onItemClick.onClick(v);
        });
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    static class SliderVH extends RecyclerView.ViewHolder {
        ImageView img;

        public SliderVH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.ivSlide);
        }
    }
}
