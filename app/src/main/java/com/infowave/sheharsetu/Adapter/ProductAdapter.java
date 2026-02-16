package com.infowave.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.ProductDetail;
import com.infowave.sheharsetu.R;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private final Context ctx;

    public interface OnContactClickListener {
        void onContactClick(@NonNull Map<String, Object> product);
    }

    private OnContactClickListener contactClickListener;

    @DrawableRes
    private final int placeholderIcon = R.drawable.ic_placeholder_circle;

    public ProductAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setOnContactClickListener(OnContactClickListener l) {
        this.contactClickListener = l;
    }

    public void setItems(List<Map<String, Object>> list) {
        items.clear();
        if (list != null)
            items.addAll(list);
        notifyDataSetChanged();
    }

    /** Append items for infinite scroll pagination */
    public void addItems(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty())
            return;
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> p = items.get(pos);

        // --- Text fields ---
        String title = getString(p, "title", "");
        String price = getString(p, "price", "");
        String city = getString(p, "city", "");
        int id = getInt(p, "id", 0);
        if (id == 0)
            id = getInt(p, "listing_id", 0);

        h.title.setText(title);
        h.price.setText(price);
        h.city.setText(city);

        String status = getString(p, "status", "active");
        boolean isSold = "sold".equalsIgnoreCase(status);
        if (h.soldBadge != null) {
            h.soldBadge.setVisibility(isSold ? View.VISIBLE : View.GONE);
        }

        String posted = getString(p, "posted_when", getString(p, "posted_time", ""));

        int finalId = id;
        h.itemView.setOnClickListener(v -> openPdp(finalId, title, price, city, posted, 0));

        h.cleanup();

        List<String> images = (List<String>) p.get("images");
        if (images == null)
            images = new ArrayList<>();

        if (images.isEmpty()) {
            String singleObj = getString(p, "imageUrl", getString(p, "image_url", ""));
            if (!TextUtils.isEmpty(singleObj)) {
                images.add(singleObj);
            }
        }

        if (images.isEmpty()) {
            images.add("");
        }

        ImageSliderAdapter sliderAdapter = new ImageSliderAdapter(images, v -> {
            openPdp(finalId, title, price, city, posted, 0);
        });
        h.vpImages.setAdapter(sliderAdapter);
        h.vpImages.setOrientation(androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL); // Ensure horizontal
        h.vpImages.setOffscreenPageLimit(1);

        // Auto-slide functionality
        int imageCount = images.size();
        if (imageCount > 1) {
            final int[] currentPage = { 0 };
            h.slideRunnable = new Runnable() {
                @Override
                public void run() {
                    if (h.vpImages != null && h.vpImages.getAdapter() != null) {
                        currentPage[0] = (currentPage[0] + 1) % imageCount;
                        h.vpImages.setCurrentItem(currentPage[0], true);
                        h.slideHandler.postDelayed(this, 5000); // 5 second interval
                    }
                }
            };
            h.slideHandler.postDelayed(h.slideRunnable, 5000);

            // Pause on touch, resume on release
            h.vpImages.getChildAt(0).setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        h.slideHandler.removeCallbacks(h.slideRunnable);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        h.slideHandler.postDelayed(h.slideRunnable, 5000);
                        break;
                }
                return false;
            });

            h.pageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    currentPage[0] = position;
                }
            };
            h.vpImages.registerOnPageChangeCallback(h.pageCallback);
        }

        // Contact button
        h.btn.setOnClickListener(v -> {
            if (contactClickListener != null) {
                contactClickListener.onContactClick(p);
            } else {
                openPdp(finalId, title, price, city, posted, 0);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.cleanup();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /* ===================== ViewHolder ===================== */
    static class VH extends RecyclerView.ViewHolder {
        androidx.viewpager2.widget.ViewPager2 vpImages;
        TextView title, price, city, soldBadge;
        Button btn;

        // Management for auto-slide
        Handler slideHandler = new Handler(Looper.getMainLooper());
        Runnable slideRunnable;
        ViewPager2.OnPageChangeCallback pageCallback;

        VH(@NonNull View v) {
            super(v);
            vpImages = v.findViewById(R.id.vpImages);
            title = v.findViewById(R.id.tvTitle);
            price = v.findViewById(R.id.tvPrice);
            city = v.findViewById(R.id.tvCity);
            btn = v.findViewById(R.id.btnContact);
            soldBadge = v.findViewById(R.id.tvSoldBadge);
        }

        void cleanup() {
            if (slideRunnable != null) {
                slideHandler.removeCallbacks(slideRunnable);
                slideRunnable = null;
            }
            if (pageCallback != null && vpImages != null) {
                vpImages.unregisterOnPageChangeCallback(pageCallback);
                pageCallback = null;
            }
        }
    }

    /* ===================== Helpers ===================== */
    private void openPdp(int id, String title, String price, String city, String posted, int imgRes) {
        Intent i = new Intent(ctx, ProductDetail.class);
        i.putExtra("listing_id", id);
        i.putExtra("title", title);
        i.putExtra("price", price);
        i.putExtra("city", city);
        i.putExtra("posted", TextUtils.isEmpty(posted) ? "" : posted);
        i.putExtra("desc", buildDescForPdp(title, price, city));
        // We no longer pass resource IDs for main flow, but keeping compatible for now
        i.putExtra("images", new int[] { imgRes != 0 ? imgRes : placeholderIcon });
        ctx.startActivity(i);
    }

    private static String buildDescForPdp(String title, String price, String city) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(title))
            sb.append(title);
        if (!TextUtils.isEmpty(price))
            sb.append(" • ").append(price);
        if (!TextUtils.isEmpty(city))
            sb.append(" • ").append(city);
        return sb.toString();
    }

    private static String getString(Map<String, Object> m, String key, String def) {
        if (m == null)
            return def;
        Object o = m.get(key);
        if (o == null)
            return def;
        String s = String.valueOf(o);
        return TextUtils.isEmpty(s) ? def : s;
    }

    private static int getInt(Map<String, Object> m, String key, int def) {
        if (m == null)
            return def;
        Object o = m.get(key);
        if (o instanceof Integer)
            return (Integer) o;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignore) {
        }
        return def;
    }
}
