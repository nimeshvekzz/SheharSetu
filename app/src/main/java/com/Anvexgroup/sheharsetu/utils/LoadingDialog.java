package com.Anvexgroup.sheharsetu.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Anvexgroup.sheharsetu.R;

/**
 * Custom Loading Dialog for SheharSetu
 * 
 * Features:
 * - Animated buy/sell themed icons that cycle in center
 * - Pulsing background circle
 * - Outer spinning progress ring
 * 
 * Usage:
 * LoadingDialog.showLoading(activity);
 * LoadingDialog.showLoading(activity, "Fetching data...");
 * LoadingDialog.hideLoading();
 */
public class LoadingDialog {

    private Dialog dialog;
    private View pulseCircle;
    private ImageView ivLoaderIcon;
    private TextView tvMessage;
    private AnimatorSet pulseAnimator;
    private Handler iconHandler;
    private Runnable iconRunnable;
    private int currentIconIndex = 0;

    // Buy/sell themed icons that cycle through
    private static final int[] LOADER_ICONS = {
            R.drawable.ic_loader_bag, // Shopping bag - buying
            R.drawable.ic_loader_tag, // Price tag - selling
            R.drawable.ic_loader_store, // Store - marketplace
            R.drawable.ic_loader_rupee, // Currency - transactions
            R.drawable.ic_loader_deal // Handshake - deals
    };

    private static final long ICON_CYCLE_DELAY = 800; // ms between icon changes

    // Static instance for easy access
    private static LoadingDialog staticInstance;

    public LoadingDialog(@NonNull Context context) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_loader, null);
        dialog.setContentView(view);

        // Make background transparent
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.dimAmount = 0.5f;
            dialog.getWindow().setAttributes(params);
        }

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        // Find views
        pulseCircle = view.findViewById(R.id.pulseCircle);
        ivLoaderIcon = view.findViewById(R.id.ivLoaderIcon);
        tvMessage = view.findViewById(R.id.tvLoadingMessage);

        // Setup animations
        setupPulseAnimation();
        setupIconCycling();
    }

    /**
     * Setup pulsing animation for the inner circle
     */
    private void setupPulseAnimation() {
        if (pulseCircle == null)
            return;

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(pulseCircle, "scaleX", 0.85f, 1.15f, 0.85f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(pulseCircle, "scaleY", 0.85f, 1.15f, 0.85f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(pulseCircle, "alpha", 0.5f, 1f, 0.5f);

        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY, alpha);
        pulseAnimator.setDuration(1000);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
    }

    /**
     * Setup icon cycling animation - cycles through buy/sell themed icons
     */
    private void setupIconCycling() {
        iconHandler = new Handler(Looper.getMainLooper());
        iconRunnable = new Runnable() {
            @Override
            public void run() {
                if (dialog != null && dialog.isShowing() && ivLoaderIcon != null) {
                    animateIconChange();
                    iconHandler.postDelayed(this, ICON_CYCLE_DELAY);
                }
            }
        };
    }

    /**
     * Animate the icon change with scale and fade effect
     */
    private void animateIconChange() {
        if (ivLoaderIcon == null)
            return;

        // Animate out
        ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(ivLoaderIcon, "scaleX", 1f, 0.3f);
        ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(ivLoaderIcon, "scaleY", 1f, 0.3f);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(ivLoaderIcon, "alpha", 1f, 0f);
        ObjectAnimator rotateOut = ObjectAnimator.ofFloat(ivLoaderIcon, "rotation", 0f, 90f);

        AnimatorSet outSet = new AnimatorSet();
        outSet.playTogether(scaleOutX, scaleOutY, fadeOut, rotateOut);
        outSet.setDuration(200);
        outSet.setInterpolator(new AccelerateDecelerateInterpolator());

        outSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Change icon
                currentIconIndex = (currentIconIndex + 1) % LOADER_ICONS.length;
                ivLoaderIcon.setImageResource(LOADER_ICONS[currentIconIndex]);

                // Animate in
                ObjectAnimator scaleInX = ObjectAnimator.ofFloat(ivLoaderIcon, "scaleX", 0.3f, 1f);
                ObjectAnimator scaleInY = ObjectAnimator.ofFloat(ivLoaderIcon, "scaleY", 0.3f, 1f);
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(ivLoaderIcon, "alpha", 0f, 1f);
                ObjectAnimator rotateIn = ObjectAnimator.ofFloat(ivLoaderIcon, "rotation", -90f, 0f);

                AnimatorSet inSet = new AnimatorSet();
                inSet.playTogether(scaleInX, scaleInY, fadeIn, rotateIn);
                inSet.setDuration(300);
                inSet.setInterpolator(new OvershootInterpolator(1.2f));
                inSet.start();
            }
        });

        outSet.start();
    }

    /**
     * Show loader with default message
     */
    public void show() {
        show("Loading...");
    }

    /**
     * Show loader with custom message
     */
    public void show(@Nullable String message) {
        if (tvMessage != null && message != null) {
            tvMessage.setText(message);
        }

        if (dialog != null && !dialog.isShowing()) {
            currentIconIndex = 0;
            if (ivLoaderIcon != null) {
                ivLoaderIcon.setImageResource(LOADER_ICONS[0]);
                ivLoaderIcon.setRotation(0f);
                ivLoaderIcon.setScaleX(1f);
                ivLoaderIcon.setScaleY(1f);
                ivLoaderIcon.setAlpha(1f);
            }

            dialog.show();
            startPulseAnimation();
            startIconCycling();
        }
    }

    /**
     * Hide the loader
     */
    public void dismiss() {
        stopPulseAnimation();
        stopIconCycling();
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                // Handle edge case where activity is finishing
            }
        }
    }

    /**
     * Check if loader is showing
     */
    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    /**
     * Update message while showing
     */
    public void setMessage(String message) {
        if (tvMessage != null) {
            tvMessage.setText(message);
        }
    }

    private void startPulseAnimation() {
        if (pulseAnimator != null && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
            // Loop the animation
            pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (dialog != null && dialog.isShowing()) {
                        pulseAnimator.start();
                    }
                }
            });
        }
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }
    }

    private void startIconCycling() {
        if (iconHandler != null && iconRunnable != null) {
            iconHandler.removeCallbacks(iconRunnable);
            iconHandler.postDelayed(iconRunnable, ICON_CYCLE_DELAY);
        }
    }

    private void stopIconCycling() {
        if (iconHandler != null && iconRunnable != null) {
            iconHandler.removeCallbacks(iconRunnable);
        }
    }

    // ==================== Static Helper Methods ====================

    /**
     * Show loader using static instance
     */
    public static void showLoading(@NonNull Context context) {
        showLoading(context, "Loading...");
    }

    /**
     * Show loader with custom message using static instance
     */
    public static void showLoading(@NonNull Context context, @Nullable String message) {
        hideLoading(); // Dismiss any existing
        staticInstance = new LoadingDialog(context);
        staticInstance.show(message);
    }

    /**
     * Hide loader using static instance
     */
    public static void hideLoading() {
        if (staticInstance != null) {
            staticInstance.dismiss();
            staticInstance = null;
        }
    }

    /**
     * Check if static loader is showing
     */
    public static boolean isLoadingShowing() {
        return staticInstance != null && staticInstance.isShowing();
    }
}
