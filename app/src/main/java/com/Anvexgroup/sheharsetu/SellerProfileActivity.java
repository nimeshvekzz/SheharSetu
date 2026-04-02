package com.Anvexgroup.sheharsetu;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.bumptech.glide.Glide;
import com.Anvexgroup.sheharsetu.Adapter.I18n;
import com.Anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.Anvexgroup.sheharsetu.Adapter.ProductAdapter;
import com.Anvexgroup.sheharsetu.net.ApiRoutes;
import com.Anvexgroup.sheharsetu.net.VolleySingleton;
import com.Anvexgroup.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellerProfileActivity extends AppCompatActivity {
    private static final String TAG = "SellerProfileActivity";

    // UI Components
    private TextView tvSellerName, tvMemberSince, tvAvatarLetter;
    private View tvNoListings;
    private ImageView ivSellerAvatar;
    private View btnCall, btnWhatsapp, btnLocation;
    private RecyclerView rvSellerListings;

    // Data
    private int sellerId = 0;
    private String sellerPhone = "";
    private ProductAdapter productAdapter;
    private List<Map<String, Object>> listings = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        LanguageManager.apply(this,
                getSharedPreferences(com.Anvexgroup.sheharsetu.core.SessionManager.PREFS, MODE_PRIVATE)
                        .getString("app_lang_code", "en"));
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        setContentView(R.layout.activity_seller_profile);

        // Apply status bar and nav bar heights to the background views
        View viewStatusBarBg = findViewById(R.id.viewStatusBarBackground);
        View viewNavBarBg = findViewById(R.id.viewNavBarBackground);
        View rootView = findViewById(R.id.root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars());
                if (viewStatusBarBg != null) {
                    viewStatusBarBg.getLayoutParams().height = systemBars.top;
                    viewStatusBarBg.requestLayout();
                }
                if (viewNavBarBg != null) {
                    viewNavBarBg.getLayoutParams().height = systemBars.bottom;
                    viewNavBarBg.requestLayout();
                }
                return insets;
            });
        }

        // Get Seller ID
        sellerId = getIntent().getIntExtra("seller_id", 0);
        if (sellerId <= 0) {
            Toast.makeText(this, I18n.t(this, "Invalid Seller ID"), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupViews();
        fetchSellerDetails();
    }

    private void setupViews() {
        // Toolbar
        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());

        // Profile Views
        tvSellerName = findViewById(R.id.tvSellerName);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        ivSellerAvatar = findViewById(R.id.ivSellerAvatar);
        tvAvatarLetter = findViewById(R.id.tvAvatarLetter);

        // Buttons
        btnCall = findViewById(R.id.btnCall);
        btnWhatsapp = findViewById(R.id.btnWhatsapp);
        btnLocation = findViewById(R.id.btnLocation);

        // Init Listings
        rvSellerListings = findViewById(R.id.rvSellerListings);
        tvNoListings = findViewById(R.id.tvNoListings);

        productAdapter = new ProductAdapter(this);
        rvSellerListings.setAdapter(productAdapter);
        rvSellerListings.setLayoutManager(new GridLayoutManager(this, 2));

        // Click Listeners
        btnCall.setOnClickListener(v -> actionCall());
        btnWhatsapp.setOnClickListener(v -> actionWhatsApp());
        btnLocation.setOnClickListener(v -> actionLocation());
    }

    private void fetchSellerDetails() {
        LoadingDialog.showLoading(this, I18n.t(this, "Loading profile..."));

        String url = ApiRoutes.BASE_URL + "/get_seller_details.php?user_id=" + sellerId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null, resp -> {
            LoadingDialog.hideLoading();
            if ("success".equalsIgnoreCase(resp.optString("status"))) {
                JSONObject data = resp.optJSONObject("data");
                if (data != null) {
                    populateProfile(data.optJSONObject("profile"));
                    populateListings(data.optJSONArray("listings"));
                }
            } else {
                Toast.makeText(this, resp.optString("message"), Toast.LENGTH_SHORT).show();
            }
        }, err -> {
            LoadingDialog.hideLoading();
            Toast.makeText(this, I18n.t(this, "Network Error"), Toast.LENGTH_SHORT).show();
        });

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1.0f));
        VolleySingleton.queue(this).add(req);
    }

    @SuppressLint("SetTextI18n")
    private void populateProfile(JSONObject p) {
        if (p == null)
            return;

        String capsName = p.optString("name", "Seller");
        tvSellerName.setText(capsName);
        tvMemberSince.setText(I18n.t(this, "Member since") + " " + p.optString("member_since"));

        // Setup Avatar Letter
        if (!capsName.isEmpty()) {
            tvAvatarLetter.setText(String.valueOf(capsName.charAt(0)).toUpperCase());
        } else {
            tvAvatarLetter.setText("S");
        }

        sellerPhone = p.optString("phone", "");

        String ava = p.optString("avatar_url", "");
        if (!ava.isEmpty()) {
            ivSellerAvatar.setVisibility(View.VISIBLE);
            Glide.with(this).load(ava).placeholder(R.drawable.ic_placeholder_circle).into(ivSellerAvatar);
        } else {
            ivSellerAvatar.setVisibility(View.INVISIBLE);
        }
    }

    private void populateListings(JSONArray arr) {
        listings.clear();
        if (arr != null && arr.length() > 0) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", o.optInt("id"));
                    m.put("title", o.optString("title"));
                    m.put("price", o.optString("price"));
                    m.put("image_url", o.optString("image_url"));
                    m.put("category", o.optString("category"));
                    m.put("city", o.optString("city"));
                    m.put("posted_when", o.optString("posted_time"));
                    m.put("status", o.optString("status", "active"));

                    // ✅ NEW: Parse images array for slider
                    List<String> images = new ArrayList<>();
                    JSONArray imgArr = o.optJSONArray("images");
                    if (imgArr != null) {
                        for (int k = 0; k < imgArr.length(); k++) {
                            String url = imgArr.optString(k, "");
                            if (!TextUtils.isEmpty(url)) {
                                images.add(url);
                            }
                        }
                    }
                    // Fallback to single image if array is empty
                    if (images.isEmpty() && !android.text.TextUtils.isEmpty(o.optString("image_url"))) {
                        images.add(o.optString("image_url"));
                    }
                    m.put("images", images);

                    listings.add(m);
                }
            }
        }

        if (listings.isEmpty()) {
            tvNoListings.setVisibility(View.VISIBLE);
            rvSellerListings.setVisibility(View.GONE);
        } else {
            tvNoListings.setVisibility(View.GONE);
            rvSellerListings.setVisibility(View.VISIBLE);
            productAdapter.setItems(listings);
        }
    }

    private void actionCall() {
        if (sellerPhone.isEmpty()) {
            Toast.makeText(this, I18n.t(this, "No phone number available"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + sellerPhone)));
        } catch (Exception e) {
            Toast.makeText(this, I18n.t(this, "Could not open dialer"), Toast.LENGTH_SHORT).show();
        }
    }

    private void actionWhatsApp() {
        if (sellerPhone.isEmpty()) {
            Toast.makeText(this, I18n.t(this, "No phone number available"), Toast.LENGTH_SHORT).show();
            return;
        }
        String url = "https://api.whatsapp.com/send?phone=" + sellerPhone;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, I18n.t(this, "WhatsApp not installed"), Toast.LENGTH_SHORT).show();
        }
    }

    private void actionLocation() {
        // Location logic: we don't have exact lat/long in profile API currently.
        // We can use the city/district to open maps.
        // For now, let's toast if no data, or open maps query.
        Toast.makeText(this, I18n.t(this, "Location details coming soon"), Toast.LENGTH_SHORT).show();
    }
}
