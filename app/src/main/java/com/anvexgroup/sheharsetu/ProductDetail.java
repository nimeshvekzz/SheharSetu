package com.anvexgroup.sheharsetu;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
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
import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.anvexgroup.sheharsetu.Adapter.I18n;
import com.anvexgroup.sheharsetu.Adapter.ImagePagerAdapter;
import com.anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.anvexgroup.sheharsetu.Adapter.SimilarAdapter;
import com.anvexgroup.sheharsetu.Adapter.ThumbAdapter;
import com.anvexgroup.sheharsetu.net.ApiRoutes;
import com.anvexgroup.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductDetail extends AppCompatActivity {

    private static final String TAG = "ProductDetail";

    private ImageView pdpBack, pdpShare, pdpSave;
    private AppBarLayout appBar;

    private ViewPager2 pdpImagePager;
    private LinearLayout pdpDots;
    private RecyclerView pdpThumbRv;
    private ThumbAdapter thumbAdapter;
    private ImagePagerAdapter pagerAdapter;

    private TextView pdpTitle, pdpPrice, pdpMeta, pdpDesc;
    private GridLayout pdpChips;
    private Chip pdpSoldChip;
    private ImageView pdpSellerAvatar;
    private TextView pdpSellerName, pdpSellerMeta, tvSellerAvatarLetter;
    private MaterialButton pdpViewProfile;

    private MaterialButton pdpCall, pdpCallWhatsapp, pdpViewLocation;

    private RecyclerView pdpSimilarRv;
    private SimilarAdapter similarAdapter;
    private TextView labelSimilar;

    private final List<Object> imageSources = new ArrayList<>();

    private final int royalBlue = Color.parseColor("#96A78D");
    private final int deepText = Color.parseColor("#111111");

    private String productTitle = "";
    private String productPrice = "";
    private String productCity = "";
    private String postedWhen = "";
    private String productDesc = "";
    private String sellerPhone = "";
    private int sellerId = 0;
    private int categoryId = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        LanguageManager.apply(this,
                getSharedPreferences(com.anvexgroup.sheharsetu.core.SessionManager.PREFS, MODE_PRIVATE)
                        .getString("app_lang_code", "en"));
        getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.black));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, android.R.color.black));
        setContentView(R.layout.activity_product_detail);

        View viewStatusBarBg = findViewById(R.id.viewStatusBarBackground);
        View viewNavBarBg = findViewById(R.id.viewNavBarBackground);
        View rootView = findViewById(R.id.root);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
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

        bindViews();
        setupGalleryShell();
        setupStaticUi();
        setupClicks();
        setupSimilarListings();

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
                for (int idRes : imgs) drawables.add(idRes);
                setGallery(drawables);
            } else {
                List<Object> ph = new ArrayList<>();
                ph.add(R.drawable.image1);
                setGallery(ph);
            }
        }
    }

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
        pdpSoldChip = findViewById(R.id.pdpSoldChip);

        pdpSellerAvatar = findViewById(R.id.pdpSellerAvatar);
        tvSellerAvatarLetter = findViewById(R.id.tvSellerAvatarLetter);
        pdpSellerName = findViewById(R.id.pdpSellerName);
        pdpSellerMeta = findViewById(R.id.pdpSellerMeta);
        pdpViewProfile = findViewById(R.id.pdpViewProfile);

        pdpCall = findViewById(R.id.pdpCall);
        pdpCallWhatsapp = findViewById(R.id.pdpCallWhatsapp);
        pdpViewLocation = findViewById(R.id.pdpViewLocation);

        pdpSimilarRv = findViewById(R.id.pdpSimilarRv);
        labelSimilar = findViewById(R.id.labelSimilar);
    }

    @SuppressLint("SetTextI18n")
    private void setupStaticUi() {
        if (appBar != null) {
            applyHeaderStyle(false);
            appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                boolean collapsed = Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange();
                applyHeaderStyle(collapsed);
            });
        }

        pdpSellerAvatar.setVisibility(View.INVISIBLE);
        if (tvSellerAvatarLetter != null) tvSellerAvatarLetter.setText("S");
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
                    + (postedWhen.isEmpty() ? "" : " • " + I18n.t(this, "Posted") + " " + postedWhen);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(s, I18n.t(this, "Share via")));
        });

        pdpSave.setOnClickListener(v -> Toast.makeText(this, "Saved/Unsaved (demo)", Toast.LENGTH_SHORT).show());

        pdpViewProfile.setOnClickListener(v -> {
            if (sellerId > 0) {
                Intent i = new Intent(ProductDetail.this, SellerProfileActivity.class);
                i.putExtra("seller_id", sellerId);
                startActivity(i);
            }
        });

        pdpCall.setOnClickListener(v -> {
            String phone = sellerPhone == null || sellerPhone.isEmpty() ? "0000000000" : sellerPhone;
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });

        pdpCallWhatsapp.setOnClickListener(v -> openWhatsApp(
                sellerPhone.isEmpty() ? "0000000000" : sellerPhone,
                I18n.t(this, "Hi, I'm interested in") + " " + productTitle + " (" + productPrice + ")"
        ));

        pdpViewLocation.setOnClickListener(v -> {
            String city = productCity == null ? "" : productCity;
            openMaps(city.isEmpty() ? "India" : city);
        });
    }

    private void fetchListing(int listingId) {
        String url = ApiRoutes.GET_LISTING_DETAILS + "?listing_id=" + listingId;
        Log.e(TAG, "========== FETCH LISTING START ==========");

        LoadingDialog.showLoading(this, I18n.t(this, "Loading listing..."));

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
                            String msg = root.optString("message", "Failed");
                            Log.e(TAG, "❌ Server returned error status: " + msg);
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONObject d = root.getJSONObject("data");
                        Log.e(TAG, "Data object found. Parsing title/price...");

                        productTitle = d.optString("title", "");
                        productPrice = d.optString("price", "");
                        productCity = d.optString("city", "");
                        productDesc = d.optString("description", "");
                        postedWhen = d.optString("posted_when", "");
                        categoryId = d.optInt("category_id", 0);
                        String listingStatus = d.optString("status", "active");
                        boolean isSold = "sold".equalsIgnoreCase(listingStatus);

                        pdpTitle.setText(productTitle);
                        pdpPrice.setText(productPrice);
                        pdpPrice.setTextColor(royalBlue);
                        String meta = productCity;
                        if (!postedWhen.isEmpty()) meta += " • " + I18n.t(this, "Posted") + " " + postedWhen;
                        pdpMeta.setText(meta);
                        pdpDesc.setText(productDesc);

                        if (pdpSoldChip != null) {
                            pdpSoldChip.setVisibility(isSold ? View.VISIBLE : View.GONE);
                        }

                        JSONObject seller = d.optJSONObject("seller");
                        if (seller != null) {
                            sellerId = seller.optInt("id", 0);
                            String sName = seller.optString("name", "Seller");
                            pdpSellerName.setText(sName);
                            if (!sName.isEmpty() && tvSellerAvatarLetter != null) {
                                tvSellerAvatarLetter.setText(String.valueOf(sName.charAt(0)).toUpperCase());
                            } else if (tvSellerAvatarLetter != null) {
                                tvSellerAvatarLetter.setText("S");
                            }

                            String mem = "";
                            if (!seller.optString("member_since", "").isEmpty()) {
                                mem = I18n.t(this, "Member since") + " " + seller.optString("member_since");
                            }
                            int cnt = seller.optInt("listings_count", 0);
                            if (cnt > 0) {
                                mem = mem.isEmpty()
                                        ? (cnt + " " + I18n.t(this, "listings"))
                                        : (mem + " • " + cnt + " " + I18n.t(this, "listings"));
                            }
                            pdpSellerMeta.setText(mem);
                            sellerPhone = seller.optString("phone", "");

                            String avatarUrl = seller.optString("avatar_url", "");
                            if (pdpSellerAvatar != null) {
                                if (!avatarUrl.isEmpty()) {
                                    pdpSellerAvatar.setVisibility(View.VISIBLE);
                                    Glide.with(ProductDetail.this)
                                            .load(avatarUrl)
                                            .placeholder(R.drawable.ic_placeholder_circle)
                                            .into(pdpSellerAvatar);
                                } else {
                                    pdpSellerAvatar.setVisibility(View.INVISIBLE);
                                }
                            }
                        }

                        JSONArray imgs = d.optJSONArray("images");
                        List<Object> srcs = new ArrayList<>();
                        if (imgs != null && imgs.length() > 0) {
                            for (int i = 0; i < imgs.length(); i++) {
                                String u = imgs.optString(i);
                                if (u != null && !u.trim().isEmpty()) srcs.add(u.trim());
                            }
                        }
                        if (srcs.isEmpty()) {
                            String single = d.optString("image_url", "");
                            if (!single.isEmpty()) srcs.add(single);
                            else srcs.add(R.drawable.image1);
                        }
                        setGallery(srcs);

                        pdpChips.removeAllViews();
                        JSONArray attrs = d.optJSONArray("attributes");
                        if (attrs != null && attrs.length() > 0) {
                            for (int i = 0; i < attrs.length(); i++) {
                                JSONObject a = attrs.getJSONObject(i);
                                String label = a.optString("label", "");
                                String val = a.optString("value", "");
                                String unit = a.optString("unit", "");

                                if (!label.isEmpty() && !val.isEmpty()) {
                                    String displayText = val + (unit.isEmpty() ? "" : " " + unit);
                                    addDetailCard(label, displayText);
                                }
                            }
                        } else {
                            addDetailCard("Status", I18n.t(this, "Verified Listing"));
                        }

                        fetchSimilarListings(listingId, categoryId);

                    } catch (Exception e) {
                        Log.e(TAG, "❌ PARSE ERROR: " + e.getMessage(), e);
                        Toast.makeText(this, I18n.t(this, "Parse error") + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "❌ NETWORK ERROR: " + err);
                    if (err.networkResponse != null) {
                        Log.e(TAG, "Status: " + err.networkResponse.statusCode);
                        try {
                            Log.e(TAG, "Body: " + new String(err.networkResponse.data, "UTF-8"));
                        } catch (Exception ignored) {
                        }
                    }
                    Toast.makeText(this, I18n.t(this, "Network error"), Toast.LENGTH_SHORT).show();
                }
        );

        com.anvexgroup.sheharsetu.net.VolleySingleton.queue(this).add(req);
    }

    private void addDetailCard(String label, String value) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_listing_detail, pdpChips, false);
        TextView tvLabel = card.findViewById(R.id.tvDetailLabel);
        TextView tvVal = card.findViewById(R.id.tvDetailValue);
        tvLabel.setText(label.toUpperCase());
        tvVal.setText(value);

        if ("availability".equalsIgnoreCase(label) || "Available".equalsIgnoreCase(value)) {
            tvVal.setTextColor(Color.parseColor("#16B381"));
        }

        pdpChips.addView(card);
    }

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
        if (postedWhen != null && !postedWhen.isEmpty()) meta += " • Posted " + postedWhen;
        pdpMeta.setText(meta);
        pdpDesc.setText(productDesc);

        bindFeatureChips(new String[]{"Demo", "Fallback"});
    }

    private void bindFeatureChips(String[] chips) {
        pdpChips.removeAllViews();
        for (String s : chips) {
            addDetailCard("Feature", s);
        }
    }

    private void applyHeaderStyle(boolean collapsed) {
        int iconTint = collapsed ? deepText : Color.WHITE;
        int bgRes = collapsed ? R.drawable.bg_header_icon_light : R.drawable.bg_header_icon_dark;
        for (ImageView v : new ImageView[]{pdpBack, pdpShare, pdpSave}) {
            if (v == null) continue;
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
        for (int i = 0; i < n; i++) updateDot(pdpDots.getChildAt(i), i == active);
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
            Toast.makeText(this, I18n.t(this, "WhatsApp not installed"), Toast.LENGTH_SHORT).show();
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

    private void setupSimilarListings() {
        if (pdpSimilarRv == null) return;

        pdpSimilarRv.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        similarAdapter = new SimilarAdapter(this);
        pdpSimilarRv.setAdapter(similarAdapter);

        if (labelSimilar != null) {
            labelSimilar.setVisibility(View.GONE);
        }
        pdpSimilarRv.setVisibility(View.GONE);
    }

    private void fetchSimilarListings(int listingId, int catId) {
        if (listingId <= 0) return;

        String url = ApiRoutes.BASE_URL + "/get_similar_listings.php?listing_id=" + listingId
                + "&category_id=" + catId + "&limit=10";

        Log.d(TAG, "Fetching similar listings: " + url);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                resp -> {
                    try {
                        JSONObject root = new JSONObject(resp);
                        if (!"success".equalsIgnoreCase(root.optString("status"))) {
                            Log.e(TAG, "Similar listings error: " + root.optString("message"));
                            hideSimilarSection();
                            return;
                        }

                        JSONArray arr = root.optJSONArray("data");
                        if (arr == null || arr.length() == 0) {
                            hideSimilarSection();
                            return;
                        }

                        List<Map<String, Object>> items = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", o.optInt("id", 0));
                            m.put("title", o.optString("title", ""));
                            m.put("price", o.optString("price", ""));
                            m.put("city", o.optString("city", ""));
                            m.put("image_url", o.optString("image_url", ""));
                            items.add(m);
                        }

                        if (similarAdapter != null) {
                            similarAdapter.setItems(items);
                        }

                        if (!items.isEmpty()) {
                            if (labelSimilar != null) labelSimilar.setVisibility(View.VISIBLE);
                            pdpSimilarRv.setVisibility(View.VISIBLE);
                        } else {
                            hideSimilarSection();
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Similar listings parse error: " + e.getMessage(), e);
                        hideSimilarSection();
                    }
                },
                err -> {
                    Log.e(TAG, "Similar listings network error: " + err);
                    hideSimilarSection();
                }
        );

        req.setShouldCache(false);
        com.anvexgroup.sheharsetu.net.VolleySingleton.queue(this).add(req);
    }

    private void hideSimilarSection() {
        if (labelSimilar != null) {
            labelSimilar.setVisibility(View.GONE);
        }
        if (pdpSimilarRv != null) {
            pdpSimilarRv.setVisibility(View.GONE);
        }
    }
}
