package com.infowave.sheharsetu;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import com.infowave.sheharsetu.Adapter.MyAdsAdapter;
import com.infowave.sheharsetu.Adapter.MyListingsAdapter;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

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

        setContentView(R.layout.activity_my_ads);

        session = new SessionManager(this);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // Views
        rvMyAds = findViewById(R.id.rvMyAds);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        cardEmptyState = findViewById(R.id.cardEmptyState);

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

    // ==================== DATA LOADING ====================

    private void fetchMyAds() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            showEmptyState();
            return;
        }

        if (!swipeRefresh.isRefreshing()) {
            LoadingDialog.showLoading(this, "Loading your ads...");
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
                            if (arr != null && arr.length() > 0) {
                                List<MyListingsAdapter.ListingItem> items = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    items.add(MyListingsAdapter.ListingItem.fromJson(arr.getJSONObject(i)));
                                }
                                adapter.setItems(items);
                                rvMyAds.setVisibility(View.VISIBLE);
                                cardEmptyState.setVisibility(View.GONE);
                            } else {
                                showEmptyState();
                            }
                        } else {
                            showEmptyState();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage());
                        showEmptyState();
                    }
                },
                error -> {
                    Log.e(TAG, "Fetch error: " + error.toString());
                    LoadingDialog.hideLoading();
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Failed to load ads", Toast.LENGTH_SHORT).show();
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
        String title = currentlySold ? "Mark as Available" : "Mark as Sold";
        String message = currentlySold
                ? "This will make your listing visible to buyers again."
                : "This will mark your listing as sold.";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Confirm", (d, w) -> doMarkSold(listingId, !currentlySold))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRepostClick(int listingId) {
        new AlertDialog.Builder(this)
                .setTitle("Repost Listing")
                .setMessage("This will bump your ad to the top of the feed so more people can see it.")
                .setPositiveButton("Repost", (d, w) -> doRepost(listingId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDeleteClick(int listingId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Listing")
                .setMessage("Are you sure? This will permanently delete your ad and cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> doDelete(listingId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== API CALLS ====================

    private void doMarkSold(int listingId, boolean markAsSold) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken))
            return;

        LoadingDialog.showLoading(this, markAsSold ? "Marking as sold..." : "Marking as available...");

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.MARK_LISTING_SOLD,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            adapter.updateItemSoldStatus(listingId, markAsSold);
                            String msg = markAsSold ? "Marked as sold!" : "Marked as available!";
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, json.optString("error", "Failed"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
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

        LoadingDialog.showLoading(this, "Reposting...");

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.REPOST_LISTING,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            // Move item to top of list
                            adapter.moveItemToTop(listingId, "Just now");
                            rvMyAds.scrollToPosition(0);
                            Toast.makeText(this, "Ad reposted! It will appear at the top of the feed.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, json.optString("error", "Repost failed"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
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

        LoadingDialog.showLoading(this, "Deleting...");

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.DELETE_LISTING,
                response -> {
                    LoadingDialog.hideLoading();
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            adapter.removeItem(listingId);
                            Toast.makeText(this, "Ad deleted", Toast.LENGTH_SHORT).show();

                            // Show empty state if no more items
                            if (adapter.getListCount() == 0) {
                                showEmptyState();
                            }
                        } else {
                            Toast.makeText(this, json.optString("error", "Delete failed"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
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
