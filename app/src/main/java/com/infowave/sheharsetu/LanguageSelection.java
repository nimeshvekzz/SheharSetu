package com.infowave.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.infowave.sheharsetu.Adapter.LanguageAdapter;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageSelection extends AppCompatActivity implements LanguageAdapter.OnLanguageClick {

    public static final String PREFS = "sheharsetu_prefs";
    public static final String KEY_LANG_CODE = "app_lang_code";
    public static final String KEY_LANG_NAME = "app_lang_name";

    private static final String TAG = "LanguageSelection";

    private RecyclerView rv;
    private ProgressBar progress;
    private Button btnContinue;

    private final List<String[]> languages = new ArrayList<>();
    private LanguageAdapter adapter;

    // current selected language
    private String[] selectedLanguage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar: black background, white icons
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        // If language already chosen, skip this screen
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = sp.getString(KEY_LANG_CODE, null);
        if (saved != null) {
            Log.d(TAG, "Saved language found: " + saved + " → skipping selection screen");
            LanguageManager.apply(this, saved);
            goNext();
            return;
        }

        setContentView(R.layout.activity_language_selection);

        rv = findViewById(R.id.rvLanguages);
        progress = findViewById(R.id.progressLanguages);
        btnContinue = findViewById(R.id.btnContinue);

        // Java से professional rounded background
        setupContinueButtonBackground();

        // Button दिखाई देगा लेकिन शुरू में disabled रहेगा (faded green)
        btnContinue.setEnabled(false);

        btnContinue.setOnClickListener(v -> {
            if (selectedLanguage == null) return;

            // lang[0] = code, lang[1] = native_name
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_LANG_CODE, selectedLanguage[0])
                    .putString(KEY_LANG_NAME, selectedLanguage[1])
                    .apply();

            LanguageManager.apply(this, selectedLanguage[0]);
            goNext();
        });

        // Grid layout for languages
        GridLayoutManager glm = new GridLayoutManager(this, 3);
        rv.setLayoutManager(glm);
        rv.setHasFixedSize(true);
        rv.setClipToPadding(false);
        rv.setPadding(
                0,
                (int) dpToPx(4),
                0,
                (int) dpToPx(4)
        );

        // GAP between language cards (professional spacing)
        rv.addItemDecoration(new GridSpacingItemDecoration(
                3,                               // span count
                (int) dpToPx(12),                // spacing between cards
                true                             // include edge
        ));

        adapter = new LanguageAdapter(languages, this);
        rv.setAdapter(adapter);

        Log.d(TAG, "onCreate: calling fetchLanguages()");
        fetchLanguages();
    }

    /** Continue button का stateful rounded background Java से बनाना */
    private void setupContinueButtonBackground() {
        float radius = dpToPx(24); // थोड़ा ज्यादा rounded pill look

        // Disabled (#6696A78D)
        GradientDrawable disabled = new GradientDrawable();
        disabled.setShape(GradientDrawable.RECTANGLE);
        disabled.setCornerRadius(radius);
        disabled.setColor(Color.parseColor("#6696A78D"));
        disabled.setStroke((int) dpToPx(1), Color.parseColor("#33FFFFFF"));

        // Pressed (#7A96A78D)
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setCornerRadius(radius);
        pressed.setColor(Color.parseColor("#7A96A78D"));
        pressed.setStroke((int) dpToPx(1), Color.parseColor("#80B6CEB4"));

        // Enabled normal (#96A78D)
        GradientDrawable enabled = new GradientDrawable();
        enabled.setShape(GradientDrawable.RECTANGLE);
        enabled.setCornerRadius(radius);
        enabled.setColor(Color.parseColor("#96A78D"));
        enabled.setStroke((int) dpToPx(1), Color.parseColor("#B6CEB4"));

        StateListDrawable stateList = new StateListDrawable();
        // order important: disabled, pressed, default
        stateList.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        stateList.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateList.addState(new int[]{}, enabled);

        btnContinue.setBackground(stateList);
        btnContinue.setTextColor(Color.WHITE);
        // हल्की elevation ताकि button background से ऊपर लगे
        btnContinue.setElevation(dpToPx(4));
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void fetchLanguages() {
        showLoading(true);

        Log.d(TAG, "fetchLanguages: URL = " + ApiRoutes.GET_LANGUAGES);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                ApiRoutes.GET_LANGUAGES,
                null,
                resp -> {
                    Log.d(TAG, "onResponse: " + resp.toString());
                    try {
                        languages.clear();
                        boolean ok = resp.optBoolean("ok", false);
                        if (!ok) {
                            Toast.makeText(this, "Failed to load languages (ok=false)", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                            return;
                        }

                        JSONArray arr = resp.optJSONArray("data");
                        if (arr == null || arr.length() == 0) {
                            Toast.makeText(this, "No languages found (empty data)", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                            return;
                        }

                        int englishIndex = -1;

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;

                            int enabled = o.optInt("enabled", 1);
                            if (enabled != 1) continue;

                            String code = o.optString("code", "").trim();
                            String nativeName = o.optString("native_name", "").trim();
                            String englishName = o.optString("english_name", "").trim();

                            if (code.isEmpty() || nativeName.isEmpty()) continue;

                            languages.add(new String[]{code, nativeName, englishName});

                            if ("en".equalsIgnoreCase(code)) {
                                englishIndex = languages.size() - 1;
                            }
                        }

                        if (languages.isEmpty()) {
                            Toast.makeText(this, "No enabled languages after parsing", Toast.LENGTH_SHORT).show();
                            showLoading(false);
                            return;
                        }

                        // English ko top pe shift karo
                        if (englishIndex > 0) {
                            String[] en = languages.remove(englishIndex);
                            languages.add(0, en);
                        }

                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error in fetchLanguages", e);
                        Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                },
                err -> {
                    String message = "Network error";
                    try {
                        if (err.networkResponse != null) {
                            int code = err.networkResponse.statusCode;
                            String body = new String(err.networkResponse.data);
                            message = "HTTP " + code + ": " + body;
                            Log.e(TAG, "Volley error body: " + body);
                        } else if (err.getMessage() != null) {
                            message = err.getMessage();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading Volley error body", e);
                    }
                    Log.e(TAG, "fetchLanguages Volley error", err);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    showLoading(false);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> h = new HashMap<>();
                h.put("Content-Type", "application/json; charset=utf-8");
                String current = getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString(KEY_LANG_CODE, "en");
                h.put("Accept-Language", current == null ? "en" : current);
                Log.d(TAG, "Request headers: " + h);
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(12000, 1, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void showLoading(boolean show) {
        if (progress != null) {
            progress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (rv != null) {
            rv.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        }
    }

    @Override
    public void onLanguageSelected(String[] lang) {
        selectedLanguage = lang;

        if (!btnContinue.isEnabled()) {
            btnContinue.setEnabled(true);
            // subtle scale animation when first enabled
            btnContinue.setScaleX(0.9f);
            btnContinue.setScaleY(0.9f);
            btnContinue.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .start();
        }
    }

    private void goNext() {
        startActivity(new Intent(this, UserInfoActivity.class));
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    /**
     * RecyclerView grid spacing decoration for professional gap between language cards.
     */
    private static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

        private final int spanCount;
        private final int spacing;
        private final boolean includeEdge;

        GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view,
                                   RecyclerView parent,
                                   RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int column = position % spanCount;                   // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount;
                outRect.right = (column + 1) * spacing / spanCount;

                if (position < spanCount) {
                    outRect.top = spacing;
                }
                outRect.bottom = spacing;
            } else {
                outRect.left = column * spacing / spanCount;
                outRect.right = spacing - (column + 1) * spacing / spanCount;
                if (position >= spanCount) {
                    outRect.top = spacing;
                }
            }
        }
    }
}
