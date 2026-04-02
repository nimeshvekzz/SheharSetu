package com.Anvexgroup.sheharsetu;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.Anvexgroup.sheharsetu.utils.TouchImageView;

import java.util.ArrayList;

public class FullScreenImageActivity extends AppCompatActivity {

    private ViewPager2 vpFullScreen;
    private ImageButton btnClose;
    private ArrayList<String> imageUrls;
    private int startPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        // Handle edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.vpFullScreen), (v, insets) -> {
            // Apply no padding to full screen image, but maybe to close button
            return insets;
        });

        vpFullScreen = findViewById(R.id.vpFullScreen);
        btnClose = findViewById(R.id.btnClose);

        imageUrls = getIntent().getStringArrayListExtra("images");
        startPosition = getIntent().getIntExtra("pos", 0);

        if (imageUrls == null)
            imageUrls = new ArrayList<>();

        btnClose.setOnClickListener(v -> finish());

        FullScreenAdapter adapter = new FullScreenAdapter(imageUrls);
        vpFullScreen.setAdapter(adapter);
        vpFullScreen.setCurrentItem(startPosition, false);
    }

    private class FullScreenAdapter extends RecyclerView.Adapter<FullScreenAdapter.VH> {
        private final ArrayList<String> images;

        public FullScreenAdapter(ArrayList<String> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TouchImageView iv = new TouchImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return new VH(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            // Load high quality image
            String url = images.get(position);
            Glide.with(h.itemView)
                    .load(url)
                    .into((TouchImageView) h.itemView);

            // Allow ViewPager2 swipe even when zoomed (TouchImageView handles scaling)
            // But TouchImageView can conflict with scrolling if not handled carefully.
            // Our TouchImageView simple implementation might consume all touch events.
            // A more advanced one would check canScrollHorizontally.
            // For now, let's trust the basic one.
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(View v) {
                super(v);
            }
        }
    }
}
