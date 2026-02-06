package com.infowave.sheharsetu;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.infowave.sheharsetu.Adapter.ImagePagerAdapter;
import com.infowave.sheharsetu.Adapter.ThumbAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Product detail page – अब API से डायनेमिक डेटा लोड करता है।
 */
public class ProductDetail extends AppCompatActivity {

    private static final String TAG = "ProductDetail";

    // Top (header)
    private ImageView pdpBack, pdpShare, pdpSave;
    private AppBarLayout appBar;

    // Gallery
    private ViewPager2 pdpImagePager;
    private LinearLayout pdpDots;
    private RecyclerView pdpThumbRv;
    private ThumbAdapter thumbAdapter;
    private ImagePagerAdapter pagerAdapter;

    // Content
    private TextView pdpTitle, pdpPrice, pdpMeta, pdpDesc;
    private ChipGroup pdpChips;
    private ImageView pdpSellerAvatar;
    private TextView pdpSellerName, pdpSellerMeta;
    private MaterialButton pdpViewProfile;

    // Bottom CTAs
    private MaterialButton pdpCall, pdpCallWhatsapp, pdpViewLocation;

    // Data holders (URLs या drawables दोनों सपोर्ट)
    private final List<Object> imageSources = new ArrayList<>();

    // Palette
    private final int royalBlue = Color.parseColor("#3E7BFA");
    private final int lavender = Color.parseColor("#D8C8FF");
    private final int deepText = Color.parseColor("#111111");

    private String productTitle = "";
    private String productPrice = "";
    private String productCity = "";
    private String postedWhen = "";
    private String productDesc = "";
    private String sellerPhone = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        setContentView(R.layout.activity_product_detail);

        bindViews();
        setupGalleryShell(); // adapters attach first
        setupStaticUi();
        setupClicks();

        // Insets for bottom bar
        View bottomBar = findViewById(R.id.bottomBar);
        if (bottomBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
                int b = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom() + b);
                return insets;
            });
        }

        // Load from API if listing_id passed; else fallback to Intent text/drawables
        int listingId = getIntent().getIntExtra("listing_id", 0);
        if (listingId > 0) {
            fetchListing(listingId);
        } else {
            String t = getIntent().getStringExtra("title");
            String p = getIntent().getStringExtra("price");
            String c = getIntent().getStringExtra("city");
            String d = getIntent().getStringExtra("desc");
            int[] imgs = getIntent().getIntArrayExtra("images");
            bindTextFallback(t, p, c, d);
            if (imgs != null && imgs.length > 0) {
                List<Object> drawables = new ArrayList<>();
                for (int idRes : imgs)
                    drawables.add(idRes);
                setGallery(drawables);
            } else {
                List<Object> ph = new ArrayList<>();
                ph.add(R.drawable.image1);
                setGallery(ph);
            }
        }
    }

    /* -------------------- Bind & setup -------------------- */

    private void bindViews() {
        pdpBack = findViewById(R.id.pdpBack);
        pdpShare = findViewById(R.id.pdpShare);
        pdpSave = findViewById(R.id.pdpSave);
        appBar = findViewById(R.id.appBar);

        pdpImagePager = findViewById(R.id.pdpImagePager);
        pdpDots = findViewById(R.id.pdpDots);
        pdpThumbRv = findViewById(R.id.pdpThumbRv);

        pdpTitle = findViewById(R.id.pdpTitle);
        pdpPrice = findViewById(R.id.pdpPrice);
        pdpMeta = findViewById(R.id.pdpMeta);
        pdpDesc = findViewById(R.id.pdpDesc);
        pdpChips = findViewById(R.id.pdpChips);

        pdpSellerAvatar = findViewById(R.id.pdpSellerAvatar);
        pdpSellerName = findViewById(R.id.pdpSellerName);
        pdpSellerMeta = findViewById(R.id.pdpSellerMeta);
        pdpViewProfile = findViewById(R.id.pdpViewProfile);

        pdpCall = findViewById(R.id.pdpCall);
        pdpCallWhatsapp = findViewById(R.id.pdpCallWhatsapp);
        pdpViewLocation = findViewById(R.id.pdpViewLocation);
    }

    @SuppressLint("SetTextI18n")
    private void setupStaticUi() {
        // Header icon tint switch on collapse/expand
        if (appBar != null) {
            applyHeaderStyle(false);
            appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                boolean collapsed = Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange();
                applyHeaderStyle(collapsed);
            });
        }

        // Seller placeholders
        pdpSellerAvatar.setImageResource(R.drawable.ic_placeholder_circle);
        pdpSellerName.setText("Seller");
        pdpSellerMeta.setText("");
    }

    private void setupGalleryShell() {
        pagerAdapter = new ImagePagerAdapter(this, imageSources, pos -> {
            ArrayList<String> urls = new ArrayList<>();
            for (Object obj : imageSources) {
                if (obj instanceof String) {
                    urls.add((String) obj);
                } else {
                    // It's a resource (Integer)
                    // We can't easily pass resource IDs to another activity expecting Strings
                    // unless we handle it
                    // For now, let's skip resources or convert to string URI
                    urls.add("android.resource://" + getPackageName() + "/" + obj);
                }
            }
            if (!urls.isEmpty()) {
                Intent i = new Intent(ProductDetail.this, FullScreenImageActivity.class);
                i.putStringArrayListExtra("images", urls);
                i.putExtra("pos", pos);
                startActivity(i);
            }
        });
        pdpImagePager.setAdapter(pagerAdapter);
        pdpImagePager.setClipToPadding(false);
        pdpImagePager.setClipChildren(false);
        pdpImagePager.setOffscreenPageLimit(3);

        CompositePageTransformer t = new CompositePageTransformer();
        t.addTransformer(new MarginPageTransformer(dp(12)));
        pdpImagePager.setPageTransformer(t);

        pdpThumbRv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        thumbAdapter = new ThumbAdapter(this, imageSources, pos -> pdpImagePager.setCurrentItem(pos, true));
        pdpThumbRv.setAdapter(thumbAdapter);

        pdpImagePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                highlightDot(position);
            }
        });
    }

    private void setupClicks() {
        pdpBack.setOnClickListener(v -> onBackPressed());

        pdpShare.setOnClickListener(v -> {
            String text = productTitle + " — " + productPrice + "\n" + productCity
                    + (postedWhen.isEmpty() ? "" : " • Posted " + postedWhen);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(s, "Share via"));
        });

        pdpSave.setOnClickListener(v -> Toast.makeText(this, "Saved/Unsaved (demo)", Toast.LENGTH_SHORT).show());

        pdpViewProfile.setOnClickListener(v -> Toast.makeText(this, "Open seller profile", Toast.LENGTH_SHORT).show());

        pdpCall.setOnClickListener(v -> {
            String phone = sellerPhone == null || sellerPhone.isEmpty() ? "0000000000" : sellerPhone;
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        pdpCallWhatsapp.setOnClickListener(v -> openWhatsApp(sellerPhone.isEmpty() ? "0000000000" : sellerPhone,
                "Hi, I'm interested in " + productTitle + " (" + productPrice + ")"));

        pdpViewLocation.setOnClickListener(v -> {
            String city = productCity == null ? "" : productCity;
            openMaps(city.isEmpty() ? "India" : city);
        });
    }

    /* -------------------- API -------------------- */

    private void fetchListing(int listingId) {
        String url = ApiRoutes.GET_LISTING_DETAILS + "?listing_id=" + listingId;

        // 🔍 DEBUG LOGS
        Log.e(TAG, "========== FETCH LISTING START ==========");
        Log.e(TAG, "URL: " + url);
        Log.e(TAG, "Listing ID: " + listingId);

        LoadingDialog.showLoading(this, "Loading listing...");

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                resp -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "✅ API RESPONSE RECEIVED");
                    Log.e(TAG, "Response Body: " + resp);

                    try {
                        JSONObject root = new JSONObject(resp);

                        if (!"success".equalsIgnoreCase(root.optString("status"))) {
                            // Logic error from server
                            String msg = root.optString("message", "Failed");
                            Log.e(TAG, "❌ Server returned error status: " + msg);
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Proceed to parse data...
                        JSONObject d = root.getJSONObject("data");
                        Log.e(TAG, "Data object found. Parsing title/price...");

                        productTitle = d.optString("title", "");
                        productPrice = d.optString("price", "");
                        productCity = d.optString("city", "");
                        productDesc = d.optString("description", "");
                        postedWhen = d.optString("posted_when", "");

                        // Bind text
                        pdpTitle.setText(productTitle);
                        pdpPrice.setText(productPrice);
                        pdpPrice.setTextColor(royalBlue);
                        String meta = productCity;
                        if (!postedWhen.isEmpty())
                            meta += " • Posted " + postedWhen;
                        pdpMeta.setText(meta);
                        pdpDesc.setText(productDesc);

                        // Seller
                        JSONObject seller = d.optJSONObject("seller");
                        if (seller != null) {
                            pdpSellerName.setText(seller.optString("name", "Seller"));
                            String mem = "";
                            if (!seller.optString("member_since", "").isEmpty()) {
                                mem = "Member since " + seller.optString("member_since");
                            }
                            int cnt = seller.optInt("listings_count", 0);
                            if (cnt > 0)
                                mem = mem.isEmpty() ? (cnt + " listings") : (mem + " • " + cnt + " listings");
                            pdpSellerMeta.setText(mem);
                            sellerPhone = seller.optString("phone", "");
                        }

                        // Images (URLs)
                        JSONArray imgs = d.optJSONArray("images");
                        List<Object> srcs = new ArrayList<>();
                        if (imgs != null && imgs.length() > 0) {
                            for (int i = 0; i < imgs.length(); i++) {
                                String u = imgs.optString(i);
                                if (u != null && !u.trim().isEmpty())
                                    srcs.add(u.trim());
                            }
                        }
                        if (srcs.isEmpty()) {
                            // fallback single image_url or placeholder
                            String single = d.optString("image_url", "");
                            if (!single.isEmpty())
                                srcs.add(single);
                            else
                                srcs.add(R.drawable.image1);
                        }
                        setGallery(srcs);

                        // 🔥 DYNAMIC ATTRIBUTES BINDING (FIXED)
                        pdpChips.removeAllViews();
                        JSONArray attrs = d.optJSONArray("attributes");
                        if (attrs != null && attrs.length() > 0) {
                            for (int i = 0; i < attrs.length(); i++) {
                                JSONObject a = attrs.getJSONObject(i);
                                String label = a.optString("label", "");
                                String val = a.optString("value", "");
                                String unit = a.optString("unit", "");

                                if (!label.isEmpty() && !val.isEmpty()) {
                                    String displayText = label + ": " + val + (unit.isEmpty() ? "" : " " + unit);
                                    addChip(displayText);
                                }
                            }
                        } else {
                            // Fallback if no specific attributes found
                            addChip("Verified Listing");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "❌ PARSE ERROR: " + e.getMessage());
                        e.printStackTrace();
                        Toast.makeText(this, "Parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "❌ NETWORK ERROR: " + err.toString());
                    if (err.networkResponse != null) {
                        Log.e(TAG, "Status: " + err.networkResponse.statusCode);
                        try {
                            Log.e(TAG, "Body: " + new String(err.networkResponse.data, "UTF-8"));
                        } catch (Exception ignored) {
                        }
                    }
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
                });

        Volley.newRequestQueue(this).add(req);
    }

    private void addChip(String text) {
        Chip chip = new Chip(this, null, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter);
        chip.setText(text);
        chip.setCheckable(false);
        // Soft blue-grey background
        chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F0F4F8")));
        chip.setTextColor(Color.BLACK);
        chip.setChipStrokeWidth(0);
        pdpChips.addView(chip);
    }

    /* -------------------- UI helpers -------------------- */

    @SuppressLint("NotifyDataSetChanged")
    private void setGallery(List<Object> sources) {
        imageSources.clear();
        imageSources.addAll(sources);
        pagerAdapter.notifyDataSetChanged();
        thumbAdapter.notifyDataSetChanged();
        buildDots(0, imageSources.size());
        pdpImagePager.setCurrentItem(0, false);
    }

    private void bindTextFallback(String title, String price, String city, String desc) {
        productTitle = title == null ? "" : title;
        productPrice = price == null ? "" : price;
        productCity = city == null ? "" : city;
        productDesc = desc == null ? "" : desc;
        postedWhen = getIntent().getStringExtra("posted");

        pdpTitle.setText(productTitle);
        pdpPrice.setText(productPrice);
        pdpPrice.setTextColor(royalBlue);
        String meta = productCity;
        if (postedWhen != null && !postedWhen.isEmpty())
            meta += " • Posted " + postedWhen;
        pdpMeta.setText(meta);
        pdpDesc.setText(productDesc);

        // Default chips (fallback)
        bindFeatureChips(new String[] { "Demo", "Fallback" });
    }

    private void bindFeatureChips(String[] chips) {
        pdpChips.removeAllViews();
        for (String s : chips) {
            Chip chip = new Chip(this, null, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Filter);
            chip.setText(s);
            chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#F2F4F7")));
            chip.setTextColor(deepText);
            chip.setChipStrokeWidth(0);
            pdpChips.addView(chip);
        }
    }

    private void applyHeaderStyle(boolean collapsed) {
        int iconTint = collapsed ? deepText : Color.WHITE;
        int bgRes = collapsed ? R.drawable.bg_header_icon_light : R.drawable.bg_header_icon_dark;
        for (ImageView v : new ImageView[] { pdpBack, pdpShare, pdpSave }) {
            if (v == null)
                continue;
            v.setBackground(ResourcesCompat.getDrawable(getResources(), bgRes, getTheme()));
            v.setImageTintList(ColorStateList.valueOf(iconTint));
        }
    }

    private void buildDots(int active, int count) {
        pdpDots.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(6), dp(6));
            lp.rightMargin = i == count - 1 ? 0 : dp(6);
            dot.setLayoutParams(lp);
            updateDot(dot, i == active);
            pdpDots.addView(dot);
        }
    }

    private void highlightDot(int active) {
        int n = pdpDots.getChildCount();
        for (int i = 0; i < n; i++)
            updateDot(pdpDots.getChildAt(i), i == active);
    }

    private void updateDot(View dot, boolean active) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(active ? Color.WHITE : 0x55FFFFFF);
        dot.setBackground(d);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void openWhatsApp(String phone, String message) {
        String ph = (phone == null || phone.isEmpty()) ? "0000000000" : phone;
        String url = "https://wa.me/" + ph + "?text=" + Uri.encode(message);
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMaps(String place) {
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(place));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, uri);
        mapIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }
}
