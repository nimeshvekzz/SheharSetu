package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SplashScreen extends AppCompatActivity {

    private static final long SPLASH_DELAY = 1800L; // 1.8s

    public static final String PREFS          = "sheharsetu_prefs";
    public static final String KEY_LANG_CODE  = "app_lang_code";
    public static final String KEY_ONBOARDED  = "onboarding_done";
    public static final String KEY_ACCESS     = "access_token";
    public static final String KEY_ACCESS_EXP = "access_expiry_epoch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);


        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);


        // Set entirely transparent status bar so gradient shows edge-to-edge
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false); // Make icons white on dark background

        // Root view padding
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // ---------- Views ----------
        View brandContainer  = findViewById(R.id.brandContainer);
        ProgressBar progress = findViewById(R.id.progress);
        TextView tvLoading   = findViewById(R.id.tvLoading);

        brandContainer.setScaleX(0.85f);
        brandContainer.setScaleY(0.85f);
        brandContainer.setAlpha(0f);
        brandContainer.setTranslationY(20f);


        progress.setAlpha(0f);
        tvLoading.setAlpha(0f);

        // 1) Brand Container slide-up + fade-in + scale-up
        brandContainer.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(600L)
                .setStartDelay(200L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 2) Bottom loading + text fade-in
        progress.animate()
                .alpha(1f)
                .setDuration(400L)
                .setStartDelay(900L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        tvLoading.animate()
                .alpha(1f)
                .setDuration(400L)
                .setStartDelay(900L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(this::routeNext, SPLASH_DELAY);
    }

    private void routeNext() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 1) Language selected?
        String lang = sp.getString(KEY_LANG_CODE, null);
        if (lang == null || lang.trim().isEmpty()) {
            go(LanguageSelection.class);
            return;
        }

        // 2) Valid token?
        if (hasValidToken(sp)) {
            go(MainActivity.class);
            return;
        }

        // 3) Onboarded / registered?
        boolean onboarded = sp.getBoolean(KEY_ONBOARDED, false);
        if (onboarded) {
            go(LoginActivity.class);
        } else {
            go(UserInfoActivity.class);
        }
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private boolean hasValidToken(SharedPreferences sp) {
        String token = sp.getString(KEY_ACCESS, null);
        long expAt   = sp.getLong(KEY_ACCESS_EXP, 0L);
        long now     = System.currentTimeMillis() / 1000L;
        return token != null && expAt > now + 15; // 15s buffer
    }
}
