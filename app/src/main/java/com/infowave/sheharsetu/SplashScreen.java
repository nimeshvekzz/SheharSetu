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


        getWindow().setStatusBarColor(
                ContextCompat.getColor(this, R.color.splashStatusBar)
        );

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        // Root view पर system bars का padding apply करो
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // ---------- Views ----------
        CardView logoCard    = findViewById(R.id.logoCard);
        TextView tvAppName   = findViewById(R.id.tvAppName);
        TextView tvTagline   = findViewById(R.id.tvTagline);
        ProgressBar progress = findViewById(R.id.progress);
        TextView tvLoading   = findViewById(R.id.tvLoading);

        logoCard.setScaleX(0.85f);
        logoCard.setScaleY(0.85f);
        logoCard.setAlpha(0f);


        tvAppName.setAlpha(0f);
        tvAppName.setTranslationY(18f);

        tvTagline.setAlpha(0f);
        tvTagline.setTranslationY(16f);

        progress.setAlpha(0f);
        tvLoading.setAlpha(0f);

        logoCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(550L)
                .setStartDelay(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 2) App name slide-up + fade-in
        tvAppName.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(420L)
                .setStartDelay(520L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tvTagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380L)
                .setStartDelay(720L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 4) Bottom loading + text fade-in
        progress.animate()
                .alpha(1f)
                .setDuration(320L)
                .setStartDelay(900L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        tvLoading.animate()
                .alpha(1f)
                .setDuration(320L)
                .setStartDelay(950L)
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
