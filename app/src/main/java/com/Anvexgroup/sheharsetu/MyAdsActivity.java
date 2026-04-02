package com.Anvexgroup.sheharsetu;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.Anvexgroup.sheharsetu.Adapter.I18n;
import com.Anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.Anvexgroup.sheharsetu.Adapter.MyAdsAdapter;
import com.Anvexgroup.sheharsetu.Adapter.MyListingsAdapter;
import com.Anvexgroup.sheharsetu.core.SessionManager;
import com.Anvexgroup.sheharsetu.net.ApiRoutes;
import com.Anvexgroup.sheharsetu.net.VolleySingleton;
import com.Anvexgroup.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * My Ads screen — shows all user's listings with actions:
 * Mark Sold/Available, Repost, Delete
 */
public class MyAdsActivity extends AppCompatActivity implements MyAdsAdapter.OnAdActionListener {

    private static final String TAG = "MyAdsActivity";

    private RecyclerView rvMyAds;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialCardView cardEmptyState;
    private MyAdsAdapter adapter;
    private SessionManager session;

    // New Views for Dashboard
    private TextView tvTotalAdsCount, tvActiveAdsCount, tvSoldAdsCount, tvListingsTitle;
    private MaterialCardView chipAllAds, chipActiveAds, chipSoldAds, chipPausedAds;

    // State for filtering
    private final List<MyListingsAdapter.ListingItem> allListings = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, ACTIVE, SOLD, PAUSED

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Black status + nav bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat ctrl = new WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView());
        ctrl.setAppearanceLightStatusBars(false);
        ctrl.setAppearanceLightNavigationBars(false);

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        setContentView(R.layout.activity_my_ads);

        session = new SessionManager(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Views
        rvMyAds = findViewById(R.id.rvMyAds);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        cardEmptyState = findViewById(R.id.cardEmptyState);

        // Dashboard views
        tvTotalAdsCount = findViewById(R.id.tvTotalAdsCount);
        tvActiveAdsCount = findViewById(R.id.tvActiveAdsCount);
        tvSoldAdsCount = findViewById(R.id.tvSoldAdsCount);
        tvListingsTitle = findViewById(R.id.tvListingsTitle);

        chipAllAds = findViewById(R.id.chipAllAds);
        chipActiveAds = findViewById(R.id.chipActiveAds);
        chipSoldAds = findViewById(R.id.chipSoldAds);
        chipPausedAds = findViewById(R.id.chipPausedAds);

        setupFilterChips();

        // RecyclerView
        rvMyAds.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyAdsAdapter(this);
        adapter.setOnAdActionListener(this);
        rvMyAds.setAdapter(adapter);

        // Pull to refresh
        swipeRefresh.setOnRefreshListener(this::fetchMyAds);

        // Empty state — Post Ad button
        findViewById(R.id.btnPostAd)
                .setOnClickListener(v -> startActivity(new Intent(this, CategorySelectActivity.class)));

        // Load data
        fetchMyAds();
    }

    // ==================== DASHBOARD & FILTERING ====================

    private void setupFilterChips() {
        if (chipAllAds == null) return;
        chipAllAds.setOnClickListener(v -> {
            currentFilter = "ALL";
            applyFilter();
        });
        chipActiveAds.setOnClickListener(v -> {
            currentFilter = "ACTIVE";
            applyFilter();
        });
        chipSoldAds.setOnClickListener(v -> {
            currentFilter = "SOLD";
            applyFilter();
        });
        chipPausedAds.setOnClickListener(v -> {
            currentFilter = "PAUSED";
            applyFilter();
        });
    }

    private void updateStats() {
        int total = allListings.size();
        int active = 0;
        int sold = 0;

        for (MyListingsAdapter.ListingItem item : allListings) {
            if (item.isSold) sold++;
            else active++;
        }

        if (tvTotalAdsCount != null) {
            tvTotalAdsCount.setText(String.valueOf(total));
            tvActiveAdsCount.setText(String.valueOf(active));
            tvSoldAdsCount.setText(String.valueOf(sold));
        }
    }

    private void applyFilter() {
        List<MyListingsAdapter.ListingItem> filtered = new ArrayList<>();
        for (MyListingsAdapter.ListingItem item : allListings) {
            if ("ALL".equals(currentFilter)) {
                filtered.add(item);
            } else if ("ACTIVE".equals(currentFilter) && !item.isSold) {
                filtered.add(item);
            } else if ("SOLD".equals(currentFilter) && item.isSold) {
                filtered.add(item);
            } else if ("PAUSED".equals(currentFilter)) {
                // Ignore for now
            }
        }

        adapter.setItems(filtered);

        if (filtered.isEmpty()) {
            showEmptyState();
        } else {
            rvMyAds.setVisibility(View.VISIBLE);
            cardEmptyState.setVisibility(View.GONE);
        }

        if (tvListingsTitle != null) {
            tvListingsTitle.setText(filtered.size() + " " + I18n.t(this, "listings"));
        }

        updateChipUI(chipAllAds, "ALL".equals(currentFilter));
        updateChipUI(chipActiveAds, "ACTIVE".equals(currentFilter));
        updateChipUI(chipSoldAds, "SOLD".equals(currentFilter));
        updateChipUI(chipPausedAds, "PAUSED".equals(currentFilter));
    }

    private void updateChipUI(MaterialCardView chip, boolean isSelected) {
        if (chip == null) return;
        TextView tv = (TextView) chip.getChildAt(0);
        if (isSelected) {
            chip.setCardBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            chip.setStrokeColor(ContextCompat.getColor(this, R.color.colorPrimary));
            if (tv != null) tv.setTextColor(Color.WHITE);
        } else {
            chip.setCardBackgroundColor(Color.WHITE);
            chip.setStrokeColor(Color.parseColor("#E5E7EB"));
            if (tv != null) tv.setTextColor(Color.parseColor("#4B5563"));
        }
    }

    // ==================== DATA LOADING ====================

    private void fetchMyAds() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            showEmptyState();
            return;
        }

        if (!swipeRefresh.isRefreshing()) {
            LoadingDialog.showLoading(this, I18n.t(this, "Loading your ads..."));
        }

        StringRequest req = new StringRequest(
                Request.Method.GET,
                ApiRoutes.GET_USER_LISTINGS,
                response -> {
                    LoadingDialog.hideLoading();
                    swipeRefresh.setRefreshing(false);

                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONArray arr = json.optJSONArray("listings");
                            allListings.clear();
                            if (arr != null && arr.length() > 0) {
                                for (int i = 0; i < arr.length(); i++) {
                                    allListings.add(MyListingsAdapter.ListingItem.fromJson(arr.getJSONObject(i)));
                                }
                            }
                            updateStats();
                            applyFilter();
                        } else {
                            allListings.clear();
                            updateStats();
                            applyFilter();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        allListings.clear();
                        updateStats();
                        applyFilter();
                    }
                },
                error -> {
                    Log.e(TAG, "Fetch error: " + error.toString());
                    LoadingDialog.hideLoading();
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, I18n.t(this, "Failed to load ads"), Toast.LENGTH_SHORT).show();
                    showEmptyState();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + accessToken);
                return h;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        req.setShouldCache(false);
        VolleySingleton.getInstance(this).add(req);
    }

    private void showEmptyState() {
        rvMyAds.setVisibility(View.GONE);
        cardEmptyState.setVisibility(View.VISIBLE);
    }

    // ==================== ACTION CALLBACKS ====================

    @Override
    public void onAdClick(int listingId) {
        Intent intent = new Intent(this, ProductDetail.class);
        intent.putExtra("listing_id", listingId);
        startActivity(intent);
    }

    @Override
    public void onMarkSoldClick(int listingId, boolean currentlySold) {
        String title = currentlySold ? I18n.t(this, "Mark as Available") : I18n.t(this, "Mark as Sold");
        String message = currentlySold
                ? I18n.t(this, "This will make your listing visible to buyers again.")
                : I18n.t(this, "This will mark your listing as sold.");

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(I18n.t(this, "Confirm"), (d, w) -> doMarkSold(listingId, !currentlySold))
                .setNegativeButton(I18n.t(this, "Cancel"), null)
                .show();
    }

    @Override
    public void onRepostClick(int listingId) {
        new AlertDialog.Builder(this)
                .setTitle(I18n.t(this, "Repost Listing"))
                .setMessage(I18n.t(this, "This will bump your ad to the top of the feed so more people can see it."))
                .setPositiveButton(I18n.t(this, "Repost"), (d, w) -> doRepost(listingId))
                .setNegativeButton(I18n.t(this, "Cancel"), null)
                .show();
    }

    @Override
    public void onEditClick(MyListingsAdapter.ListingItem item) {
        Intent intent = new Intent(this, DynamicFormActivity.class);
        intent.putExtra(DynamicFormActivity.EXTRA_CATEGORY, item.category);
        intent.putExtra("category_id", String.valueOf(item.categoryId));
        intent.putExtra("subcategory_id", String.valueOf(item.subcategoryId));
        intent.putExtra("edit_listing_id", item.listingId);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(int listingId) {
        new AlertDialog.Builder(this)
                .setTitle(I18n.t(this, "Delete Listing"))
                .setMessage(I18n.t(this, "Are you sure? This will permanently delete your ad and cannot be undone."))
                .setPositiveButton(I18n.t(this, "Delete"), (d, w) -> doDelete(listingId))
                .setNegativeButton(I18n.t(this, "Cancel"), null)
                .show();
    }

    // ==================== API CALLS ====================

    private void doMarkSold(int listingId, boolean markAsSold) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken))
            return;

        LoadingDialog.showLoading(this, markAsSold ? I18n.t(this, "Marking as sold...") : I18n.t(this, "Marking as available..."));

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.MARK_LISTING_SOLD,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            for (MyListingsAdapter.ListingItem item : allListings) {
                                if (item.listingId == listingId) {
                                    item.isSold = markAsSold;
                                    break;
                                }
                            }
                            updateStats();
                            applyFilter();
                            String msg = markAsSold ? I18n.t(this, "Marked as sold!") : I18n.t(this, "Marked as available!");
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, json.optString("error", I18n.t(this, "Failed")), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Error processing response"), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, I18n.t(this, "Network error"), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + accessToken);
                return h;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("listing_id", String.valueOf(listingId));
                p.put("is_sold", markAsSold ? "1" : "0");
                return p;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    private void doRepost(int listingId) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken))
            return;

        LoadingDialog.showLoading(this, I18n.t(this, "Reposting..."));

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.REPOST_LISTING,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            // Move item to top of list with updated repost count
                            int repostCount = json.optInt("repost_count", 0);
                            for (int i = 0; i < allListings.size(); i++) {
                                if (allListings.get(i).listingId == listingId) {
                                    MyListingsAdapter.ListingItem item = allListings.remove(i);
                                    item.isSold = false;
                                    item.postedWhen = "Just now";
                                    item.repostCount = repostCount;
                                    allListings.add(0, item);
                                    break;
                                }
                            }
                            updateStats();
                            applyFilter();
                            rvMyAds.scrollToPosition(0);
                            Toast.makeText(this, I18n.t(this, "Ad reposted! It will appear at the top of the feed."),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, json.optString("error", I18n.t(this, "Repost failed")),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Error processing response"), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, I18n.t(this, "Network error"), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + accessToken);
                return h;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("listing_id", String.valueOf(listingId));
                return p;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    private void doDelete(int listingId) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken))
            return;

        LoadingDialog.showLoading(this, I18n.t(this, "Deleting..."));

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.DELETE_LISTING,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            for (int i = 0; i < allListings.size(); i++) {
                                if (allListings.get(i).listingId == listingId) {
                                    allListings.remove(i);
                                    break;
                                }
                            }
                            updateStats();
                            applyFilter();
                            Toast.makeText(this, I18n.t(this, "Ad deleted"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, json.optString("error", I18n.t(this, "Delete failed")),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Error processing response"), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, I18n.t(this, "Network error"), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + accessToken);
                return h;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("listing_id", String.valueOf(listingId));
                return p;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }
}
