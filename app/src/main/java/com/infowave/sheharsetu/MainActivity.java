package com.infowave.sheharsetu;

import static android.widget.Toast.makeText;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.infowave.sheharsetu.Adapter.CategoryAdapter;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.Adapter.ProductAdapter;
import com.infowave.sheharsetu.Adapter.SubFilterGridAdapter;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // ===== Views (Header) =====
    private ImageView btnDrawer;
    private TextInputLayout tiSearch;
    private TextInputEditText etSearch;
    private ActivityResultLauncher<Intent> speechLauncher;

    // ===== Lists =====
    private RecyclerView rvCategories, rvSubFiltersGrid, rvProducts;
    private MaterialButtonToggleGroup toggleNewOld;
    private TextView tvSectionTitle;

    // ===== Bottom banner =====
    private ImageButton btnPost, btnHelp;
    private TextView tvMarquee;

    // ===== Drawer =====
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // ===== Data (dynamic) =====
    private final List<Map<String, Object>> categories = new ArrayList<>();
    private final Map<Integer, List<Map<String, Object>>> mapSubFilters = new HashMap<>();
    private final List<Map<String, Object>> currentProducts = new ArrayList<>();

    // ===== State =====
    private int selectedCategoryId = -1;
    private int selectedSubFilterId = -1; // -1 = none (sub grid hidden), 0 = ALL
    private Boolean showNew = null; // null = all, true=new, false=old
    private String searchQuery = "";

    // ===== Adapters =====
    private CategoryAdapter catAdapter;
    private ProductAdapter productAdapterRef;

    // ===== Locale Prefs =====
    private static final String PREFS = LanguageSelection.PREFS;
    private static final String KEY_LANG = LanguageSelection.KEY_LANG_CODE;

    // ===== Session =====
    private SessionManager session;

    // ===== User Profile Cache =====
    private String cachedUserName = "User";
    private String cachedUserPhone = "";
    private TextView tvNavUserName; // Reference to nav header TextView
    private TextView tvNavUserPhone; // Reference to nav header TextView

    // ===== Pagination =====
    private static final int PAGE = 1;
    private static final int LIMIT = 50;

    // ✅ Network optimization + correctness
    private String lastProductsUrl = null;
    private boolean productsInFlight = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate() started");

        // Apply saved locale first
        applySavedLocale();
        session = new SessionManager(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        bindHeader();

        rvCategories = findViewById(R.id.rvCategories);
        rvSubFiltersGrid = findViewById(R.id.rvSubFiltersGrid);
        rvProducts = findViewById(R.id.rvProducts);
        toggleNewOld = findViewById(R.id.toggleNewOld);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);

        // Initial state of New/Old toggle
        if (toggleNewOld != null) {
            toggleNewOld.setVisibility(View.GONE);
            toggleNewOld.clearChecked();
            showNew = null;
        }

        btnPost = findViewById(R.id.btnPost);
        btnHelp = findViewById(R.id.btnHelp);
        tvMarquee = findViewById(R.id.tvMarquee);
        if (tvMarquee != null)
            tvMarquee.setSelected(true);

        TextView tvLangBadge = findViewById(R.id.tvLangBadge);
        if (tvLangBadge != null) {
            tvLangBadge.setText(I18n.t(this, "Language") + ": " + session.getLangName());
        }

        setupVoiceLauncher();
        setupSearch();
        setupLanguageToggle();
        setupAppDrawer();

        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvSubFiltersGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvProducts.setLayoutManager(new GridLayoutManager(this, 2));

        setupAdapters();

        btnPost.setOnClickListener(v -> startActivity(new Intent(this, CategorySelectActivity.class)));
        btnHelp.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));

        prefetchAndApplyStaticTexts();

        // Load data
        showProducts();

        Log.d(TAG, "Initial fetch: categories + products + user profile");
        LoadingDialog.showLoading(this, "Loading data...");
        fetchCategories();
        fetchProducts(); // featured on first load
        fetchUserProfileOnStartup(); // Fetch user profile immediately on app start

        toggleNewOld.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked)
                return;
            ensureProductsView();
            showNew = (id == R.id.btnShowNew) ? Boolean.TRUE : Boolean.FALSE;
            Log.d(TAG, "Toggle New/Old changed: showNew=" + showNew);
            fetchProducts();
        });
    }

    // ================= Header/Search/Voice =================

    private void bindHeader() {
        btnDrawer = findViewById(R.id.btnDrawer);
        tiSearch = findViewById(R.id.tiSearch);
        etSearch = findViewById(R.id.etSearch);
    }

    private void setupVoiceLauncher() {
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> list = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (list != null && !list.isEmpty()) {
                            etSearch.setText(list.get(0));
                            performSearch(list.get(0));
                        }
                    }
                });
        if (tiSearch != null) {
            tiSearch.setEndIconOnClickListener(v -> startVoiceInput());
        }
    }

    private void setupSearch() {
        if (etSearch == null)
            return;
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                performSearch(q);
                return true;
            }
            return false;
        });
    }

    private void startVoiceInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, I18n.t(this, "Speak to search…"));
        try {
            speechLauncher.launch(i);
        } catch (Exception e) {
            makeText(this, I18n.t(this, "Voice search not available"), Toast.LENGTH_SHORT).show();
        }
    }

    private void performSearch(String query) {
        ensureProductsView();
        searchQuery = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.ROOT).trim();
        Log.d(TAG, "performSearch(): q=" + searchQuery);
        // allow same URL to re-fetch when user hits search again
        productsInFlight = false;
        fetchProducts();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
        Log.d(TAG, "applySavedLocale(): " + lang);
        LanguageManager.apply(this, lang);
    }

    private void setupLanguageToggle() {
        if (btnDrawer == null)
            return;

        btnDrawer.setOnClickListener(v -> {
            if (drawerLayout != null)
                drawerLayout.openDrawer(GravityCompat.START);
        });

        btnDrawer.setOnLongClickListener(v -> {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String cur = sp.getString(KEY_LANG, "en");
            String next = cur.equals("en") ? "hi" : "en";
            String nextName = next.equals("en") ? "English" : "हिन्दी";

            sp.edit()
                    .putString(KEY_LANG, next)
                    .putString(LanguageSelection.KEY_LANG_NAME, nextName)
                    .apply();

            LanguageManager.apply(this, next);
            makeText(this, I18n.t(this, "Language") + ": " + nextName, Toast.LENGTH_SHORT).show();

            recreate();
            return true;
        });
    }

    // ================= Drawer =================

    private void setupAppDrawer() {
        Log.d(TAG, "========== SETUP APP DRAWER START ==========");
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        Log.d(TAG, "drawerLayout: " + (drawerLayout != null ? "FOUND" : "NULL"));
        Log.d(TAG, "navigationView: " + (navigationView != null ? "FOUND" : "NULL"));
        if (drawerLayout == null || navigationView == null) {
            Log.w(TAG, "❌ setupAppDrawer: drawerLayout or navigationView is NULL - RETURNING");
            return;
        }

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        View header = navigationView.getHeaderView(0);
        Log.d(TAG, "Navigation header view: " + (header != null ? "FOUND" : "NULL"));
        if (header != null) {
            ImageView ivProfile = header.findViewById(R.id.ivProfile);
            tvNavUserName = header.findViewById(R.id.tvUserName);
            tvNavUserPhone = header.findViewById(R.id.tvUserPhone);
            ImageView ivEdit = header.findViewById(R.id.ivEdit);

            Log.d(TAG,
                    "Header views - tvNavUserName: " + (tvNavUserName != null ? "FOUND" : "NULL") + ", tvNavUserPhone: "
                            + (tvNavUserPhone != null ? "FOUND" : "NULL"));

            // Display cached user data (will be updated when API response arrives)
            Log.d(TAG, "Setting initial cached user data: name=" + cachedUserName + ", phone=" + cachedUserPhone);
            if (tvNavUserName != null) {
                tvNavUserName.setText(cachedUserName);
            }
            if (tvNavUserPhone != null) {
                tvNavUserPhone.setText(cachedUserPhone);
            }

            View.OnClickListener openProfileClick = v -> {
                Intent i = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(i);
            };

            header.setOnClickListener(openProfileClick);
            if (ivProfile != null)
                ivProfile.setOnClickListener(openProfileClick);
            if (ivEdit != null)
                ivEdit.setOnClickListener(openProfileClick);
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            item.setChecked(true);
            drawerLayout.closeDrawer(GravityCompat.START);
            navigationView.setItemIconTintList(null);

            if (id == R.id.nav_home) {
                Toast.makeText(MainActivity.this, I18n.t(this, "Home"), Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_post) {
                startActivity(new Intent(MainActivity.this, CategorySelectActivity.class));

            } else if (id == R.id.nav_my_ads) {
                Toast.makeText(MainActivity.this, I18n.t(this, "My Ads"), Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_notifications) {
                Toast.makeText(MainActivity.this, I18n.t(this, "Notifications"), Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_invite) {
                shareApp();

            } else if (id == R.id.nav_rate) {
                rateUs();

            } else if (id == R.id.nav_contact) {
                startActivity(new Intent(MainActivity.this, ContactUsActivity.class));

            } else if (id == R.id.nav_about) {
                startActivity(new Intent(MainActivity.this, AboutUsActivity.class));

            } else if (id == R.id.nav_logout) {
                doLogout();
            } else {
                Toast.makeText(MainActivity.this, I18n.t(this, "Coming soon"), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void doLogout() {
        getSharedPreferences("user", MODE_PRIVATE).edit().clear().apply();
        makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        Intent i = new Intent(this, LanguageSelection.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ================= Static UI translation =================

    private void prefetchAndApplyStaticTexts() {
        List<String> keys = new ArrayList<>();

        if (tiSearch != null && tiSearch.getHint() != null)
            keys.add(tiSearch.getHint().toString());
        if (tvSectionTitle != null && tvSectionTitle.getText() != null)
            keys.add(tvSectionTitle.getText().toString());
        if (tvMarquee != null && tvMarquee.getText() != null)
            keys.add(tvMarquee.getText().toString());

        keys.add("Speak to search…");
        keys.add("Voice search not available");
        keys.add("Home");
        keys.add("My Ads");
        keys.add("Notifications");
        keys.add("Coming soon");
        keys.add("Categories error");
        keys.add("Parse categories failed");
        keys.add("Network error (categories)");
        keys.add("Subcategories error");
        keys.add("Parse subcategories failed");
        keys.add("Network error (subcategories)");
        keys.add("Products error");
        keys.add("Parse products failed");
        keys.add("Network error (products)");
        keys.add("Share via");
        keys.add("No app found to share");

        I18n.prefetch(this, keys, () -> {
            if (tiSearch != null)
                I18n.translateAndApplyHint(tiSearch, this);
            if (tvSectionTitle != null && tvSectionTitle.getText() != null) {
                tvSectionTitle.setText(I18n.t(this, tvSectionTitle.getText().toString()));
            }
            if (tvMarquee != null && tvMarquee.getText() != null) {
                tvMarquee.setText(I18n.t(this, tvMarquee.getText().toString()));
            }
        });
    }

    // ================= Adapters =================

    private void setupAdapters() {
        catAdapter = new CategoryAdapter(categories, cat -> {
            selectedCategoryId = toInt(cat.get("id"), -1);
            selectedSubFilterId = -1;
            showNew = null;
            clearSearch();

            Log.d(TAG, "Category selected: categoryId=" + selectedCategoryId);

            if (toggleNewOld != null) {
                toggleNewOld.setVisibility(View.GONE);
                toggleNewOld.clearChecked();
            }

            // Allow products to refetch after category change
            productsInFlight = false;
            lastProductsUrl = null;

            fetchSubFilters(selectedCategoryId);
        });
        rvCategories.setAdapter(catAdapter);

        productAdapterRef = new ProductAdapter(this);
        rvProducts.setAdapter(productAdapterRef);
    }

    private void bindProducts(List<Map<String, Object>> items) {
        Log.d(TAG, "bindProducts(): items=" + (items == null ? 0 : items.size()));
        rvProducts.setVisibility(View.VISIBLE);
        if (productAdapterRef != null)
            productAdapterRef.setItems(items);
    }

    private void showSubFilters() {
        rvProducts.setVisibility(View.GONE);
        if (tvSectionTitle != null)
            tvSectionTitle.setVisibility(View.GONE);
        rvSubFiltersGrid.setVisibility(View.VISIBLE);
    }

    private void showProducts() {
        rvSubFiltersGrid.setVisibility(View.GONE);
        if (tvSectionTitle != null)
            tvSectionTitle.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.VISIBLE);
        if (tvSectionTitle != null) {
            tvSectionTitle.setText(I18n.t(this, getString(R.string.featured_listings)));
        }
    }

    private void ensureProductsView() {
        if (rvSubFiltersGrid.getVisibility() == View.VISIBLE)
            showProducts();
    }

    private void clearSearch() {
        searchQuery = "";
        if (etSearch != null && etSearch.getText() != null)
            etSearch.setText("");
    }

    // ================= Network: URLs =================

    private String urlCategories() {
        return ApiRoutes.BASE_URL + "/list_categories.php";
    }

    private String urlSubcategories(int categoryId) {
        return ApiRoutes.BASE_URL + "/list_subcategories.php?category_id=" + categoryId;
    }

    private String urlProducts() {
        StringBuilder sb = new StringBuilder(ApiRoutes.BASE_URL)
                .append("/list_products.php?page=").append(PAGE)
                .append("&limit=").append(LIMIT)
                .append("&sort=newest");

        if (selectedCategoryId > 0)
            sb.append("&category_id=").append(selectedCategoryId);
        if (selectedSubFilterId > 0)
            sb.append("&subcategory_id=").append(selectedSubFilterId);
        if (!TextUtils.isEmpty(searchQuery))
            sb.append("&q=").append(android.net.Uri.encode(searchQuery));
        if (showNew != null)
            sb.append("&is_new=").append(showNew ? "1" : "0");

        return sb.toString();
    }

    // ================= Network: Fetchers =================

    private void fetchCategories() {
        final String url = urlCategories();
        Log.d(TAG, "========== FETCH CATEGORIES START ==========");
        Log.d(TAG, "fetchCategories(): url=" + url);
        Log.d(TAG, "fetchCategories(): timestamp=" + System.currentTimeMillis());

        @SuppressLint("NotifyDataSetChanged")
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
                    Log.d(TAG, "========== FETCH CATEGORIES RESPONSE ==========");
                    Log.d(TAG, "fetchCategories() response=" + safeJsonSnippet(resp));
                    Log.d(TAG, "fetchCategories() response.status=" + resp.optString("status"));
                    Log.d(TAG, "fetchCategories() response.has('data')=" + resp.has("data"));
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Log.e(TAG, "fetchCategories(): status != success, status=" + resp.optString("status"));
                            makeText(this, I18n.t(this, "Categories error"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray arr = resp.optJSONArray("data");
                        categories.clear();

                        List<String> catNameKeys = new ArrayList<>();

                        if (arr != null) {
                            Log.d(TAG, "fetchCategories(): dataCount=" + arr.length());
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Log.d(TAG, "--- Category[" + i + "] RAW JSON: " + o.toString());

                                Map<String, Object> m = new HashMap<>();
                                int catId = o.optInt("id", 0);
                                m.put("id", catId);
                                String nameEn = o.optString("name", "");
                                m.put("name", nameEn);

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl))
                                    iconUrl = o.optString("icon_url", "");
                                m.put("iconUrl", iconUrl);

                                Log.d(TAG, "Category[" + i + "] id=" + catId + " name=" + nameEn);
                                Log.d(TAG, "Category[" + i + "] icon=" + o.optString("icon", "<empty>"));
                                Log.d(TAG, "Category[" + i + "] icon_url=" + o.optString("icon_url", "<empty>"));
                                Log.d(TAG, "Category[" + i + "] FINAL iconUrl=" + iconUrl);

                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                categories.add(m);

                                if (!TextUtils.isEmpty(nameEn))
                                    catNameKeys.add(nameEn);
                            }
                        } else {
                            Log.e(TAG, "fetchCategories(): data array is NULL!");
                        }

                        Log.d(TAG, "fetchCategories(): Total categories parsed=" + categories.size());
                        Log.d(TAG, "fetchCategories(): Notifying adapter...");
                        catAdapter.notifyDataSetChanged();
                        Log.d(TAG, "========== FETCH CATEGORIES COMPLETE ==========");

                        I18n.prefetch(this, catNameKeys, () -> {
                            for (Map<String, Object> m : categories) {
                                Object nObj = m.get("name");
                                if (nObj != null) {
                                    String en = String.valueOf(nObj);
                                    m.put("name", I18n.t(this, en));
                                }
                            }
                            catAdapter.notifyDataSetChanged();
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "fetchCategories(): parse exception", e);
                        makeText(this, I18n.t(this, "Parse categories failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    Log.e(TAG, "fetchCategories() error=" + buildVolleyError(err), err);
                    makeText(this, I18n.t(this, "Network error (categories)"), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Accept-Language", I18n.lang(MainActivity.this));
                return headers;
            }
        };

        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchSubFilters(int categoryId) {
        final String url = urlSubcategories(categoryId);
        Log.d(TAG, "========== FETCH SUBFILTERS START ==========");
        Log.d(TAG, "fetchSubFilters(): categoryId=" + categoryId + " url=" + url);
        Log.d(TAG, "fetchSubFilters(): timestamp=" + System.currentTimeMillis());

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
                    Log.d(TAG, "========== FETCH SUBFILTERS RESPONSE ==========");
                    Log.d(TAG, "fetchSubFilters() response=" + safeJsonSnippet(resp));
                    Log.d(TAG, "fetchSubFilters() response.status=" + resp.optString("status"));
                    Log.d(TAG, "fetchSubFilters() response.has('data')=" + resp.has("data"));
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Log.e(TAG, "fetchSubFilters(): status != success, status=" + resp.optString("status"));
                            makeText(this, I18n.t(this, "Subcategories error"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray arr = resp.optJSONArray("data");
                        List<Map<String, Object>> subs = new ArrayList<>();

                        Map<String, Object> all = new HashMap<>();
                        all.put("id", 0);
                        all.put("name", getString(R.string.sub_all));
                        all.put("iconRes", R.drawable.ic_placeholder_circle);
                        all.put("hasNewOld", false);
                        subs.add(all);

                        if (arr != null) {
                            Log.d(TAG, "fetchSubFilters(): dataCount=" + arr.length());
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Log.d(TAG, "--- Subcategory[" + i + "] RAW JSON: " + o.toString());

                                Map<String, Object> m = new HashMap<>();
                                int subId = o.optInt("id", 0);
                                m.put("id", subId);
                                m.put("category_id", o.optInt("category_id", categoryId));
                                String subName = o.optString("name", "");
                                m.put("name", subName);

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl))
                                    iconUrl = o.optString("icon_url", "");
                                m.put("iconUrl", iconUrl);

                                Log.d(TAG, "Subcategory[" + i + "] id=" + subId + " name=" + subName);
                                Log.d(TAG, "Subcategory[" + i + "] icon=" + o.optString("icon", "<empty>"));
                                Log.d(TAG, "Subcategory[" + i + "] icon_url=" + o.optString("icon_url", "<empty>"));
                                Log.d(TAG, "Subcategory[" + i + "] FINAL iconUrl=" + iconUrl);

                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                subs.add(m);
                            }
                        } else {
                            Log.e(TAG, "fetchSubFilters(): data array is NULL!");
                        }

                        Log.d(TAG, "fetchSubFilters(): Total subcategories parsed=" + subs.size());
                        mapSubFilters.put(categoryId, subs);

                        rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
                            selectedSubFilterId = toInt(sub.get("id"), 0);
                            clearSearch();
                            showProducts();

                            Log.d(TAG, "Subcategory selected: subId=" + selectedSubFilterId
                                    + " hasNewOld=" + toBool(sub.get("hasNewOld"), false));

                            if (selectedSubFilterId > 0) {
                                boolean hasNewOld = toBool(sub.get("hasNewOld"), false);
                                if (hasNewOld && toggleNewOld != null) {
                                    toggleNewOld.setVisibility(View.VISIBLE);
                                } else if (toggleNewOld != null) {
                                    toggleNewOld.setVisibility(View.GONE);
                                    showNew = null;
                                    toggleNewOld.clearChecked();
                                }
                            } else {
                                if (toggleNewOld != null) {
                                    toggleNewOld.setVisibility(View.GONE);
                                    showNew = null;
                                    toggleNewOld.clearChecked();
                                }
                            }

                            productsInFlight = false;
                            lastProductsUrl = null;
                            fetchProducts();
                        }));

                        showSubFilters();
                        catAdapter.setSelectedId(selectedCategoryId);

                    } catch (Exception e) {
                        Log.e(TAG, "fetchSubFilters(): parse exception", e);
                        makeText(this, I18n.t(this, "Parse subcategories failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    Log.e(TAG, "fetchSubFilters() error=" + buildVolleyError(err), err);
                    makeText(this, I18n.t(this, "Network error (subcategories)"), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Accept-Language", I18n.lang(MainActivity.this));
                return headers;
            }
        };
        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchProducts() {
        final String url = urlProducts();

        Log.d(TAG, "========== FETCH PRODUCTS START ==========");
        Log.d(TAG, "fetchProducts(): url=" + url);
        Log.d(TAG, "fetchProducts(): timestamp=" + System.currentTimeMillis());
        Log.d(TAG, "fetchProducts() filters: categoryId=" + selectedCategoryId
                + " subId=" + selectedSubFilterId
                + " showNew=" + showNew
                + " q=" + searchQuery);

        // Prevent parallel duplicate calls
        if (productsInFlight) {
            Log.w(TAG, "fetchProducts(): skipped (already in flight)");
            return;
        }

        // If same URL already loaded successfully, skip
        if (lastProductsUrl != null && lastProductsUrl.equals(url)) {
            Log.w(TAG, "fetchProducts(): skipped (same as last success URL)");
            return;
        }

        productsInFlight = true;

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
                    productsInFlight = false;

                    Log.d(TAG, "========== FETCH PRODUCTS RESPONSE ==========");
                    Log.d(TAG, "fetchProducts() response=" + safeJsonSnippet(resp));
                    Log.d(TAG, "fetchProducts() response.status=" + resp.optString("status"));
                    Log.d(TAG, "fetchProducts() response.has('data')=" + resp.has("data"));

                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Log.e(TAG, "fetchProducts(): status != success, status=" + resp.optString("status"));
                            makeText(this, I18n.t(this, "Products error"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray arr = resp.optJSONArray("data");
                        currentProducts.clear();

                        if (arr != null) {
                            Log.d(TAG, "fetchProducts(): dataCount=" + arr.length());

                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);

                                // Log first 3 products in detail, rest in summary
                                if (i < 3) {
                                    Log.d(TAG, "--- Product[" + i + "] RAW JSON: " + o.toString());
                                }

                                Map<String, Object> m = new HashMap<>();
                                int prodId = o.optInt("id", 0);
                                m.put("id", prodId);
                                m.put("categoryId", o.optInt("category_id", 0));
                                m.put("subFilterId", o.optInt("subcategory_id", 0));
                                String title = o.optString("title", "");
                                m.put("title", title);

                                m.put("price",
                                        o.opt("price") == null
                                                ? ""
                                                : String.valueOf(o.opt("price")));

                                m.put("city", o.optString("city", ""));

                                // ✅ FIX: Check cover_image field first (from logs)
                                String imageUrl = "";
                                if (!o.isNull("cover_image")) {
                                    imageUrl = o.optString("cover_image", "");
                                } else if (!o.isNull("image_url")) {
                                    imageUrl = o.optString("image_url", "");
                                } else if (!o.isNull("image")) {
                                    imageUrl = o.optString("image", "");
                                }

                                // ✅ FIX: Prepend base URL if path is relative
                                if (!TextUtils.isEmpty(imageUrl) && !imageUrl.startsWith("http")) {
                                    // Remove leading slash if present
                                    if (imageUrl.startsWith("/")) {
                                        imageUrl = imageUrl.substring(1);
                                    }
                                    imageUrl = "https://magenta-owl-444153.hostingersite.com/" + imageUrl;
                                }
                                m.put("imageUrl", imageUrl);

                                if (i < 3) {
                                    Log.d(TAG, "Product[" + i + "] id=" + prodId + " title=" + title);
                                    Log.d(TAG,
                                            "Product[" + i + "] cover_image=" + o.optString("cover_image", "<empty>"));
                                    Log.d(TAG, "Product[" + i + "] image_url=" + o.optString("image_url", "<empty>"));
                                    Log.d(TAG, "Product[" + i + "] image=" + o.optString("image", "<empty>"));
                                    Log.d(TAG, "Product[" + i + "] FINAL imageUrl=" + imageUrl);
                                    Log.d(TAG, "Product[" + i + "] imageUrl.length=" + imageUrl.length());
                                    Log.d(TAG, "Product[" + i + "] imageUrl.startsWith('http')="
                                            + imageUrl.startsWith("http"));
                                }

                                int rawIsNew = o.isNull("is_new") ? -1 : o.optInt("is_new", -1);
                                boolean isNew = (rawIsNew == 1);
                                m.put("isNew", isNew);

                                currentProducts.add(m);
                            }
                        } else {
                            Log.e(TAG, "fetchProducts(): data array is NULL!");
                        }

                        Log.d(TAG, "fetchProducts(): Total products parsed=" + currentProducts.size());
                        Log.d(TAG, "fetchProducts(): Binding products to adapter...");
                        bindProducts(new ArrayList<>(currentProducts));
                        LoadingDialog.hideLoading();
                        Log.d(TAG, "========== FETCH PRODUCTS COMPLETE ==========");

                        if (tvSectionTitle != null) {
                            tvSectionTitle.setText(I18n.t(this, getString(R.string.featured_listings)));
                        }

                        // ✅ mark success URL only after success
                        lastProductsUrl = url;

                    } catch (Exception e) {
                        Log.e(TAG, "fetchProducts(): parse exception", e);
                        makeText(this, I18n.t(this, "Parse products failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    productsInFlight = false;
                    LoadingDialog.hideLoading();

                    Log.e(TAG, "fetchProducts() error=" + buildVolleyError(err), err);
                    makeText(this, I18n.t(this, "Network error (products)"), Toast.LENGTH_SHORT).show();

                    // allow retry next time
                    // (do NOT set lastProductsUrl on error)
                }) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Accept-Language", I18n.lang(MainActivity.this));
                return headers;
            }
        };
        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(20000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }

    // ================= Back navigation =================

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (rvSubFiltersGrid.getVisibility() == View.VISIBLE) {
            showProducts();
            selectedSubFilterId = -1;
            productsInFlight = false;
            lastProductsUrl = null;
            fetchProducts();
            return;
        }
        if (selectedCategoryId != -1) {
            selectedCategoryId = -1;
            selectedSubFilterId = -1;
            showNew = null;
            if (toggleNewOld != null)
                toggleNewOld.setVisibility(View.GONE);
            if (catAdapter != null)
                catAdapter.setSelectedId(-1);

            showProducts();
            clearSearch();

            productsInFlight = false;
            lastProductsUrl = null;
            fetchProducts();
            return;
        }
        super.onBackPressed();
    }

    // ================= Helpers =================

    private static int toInt(Object o, int def) {
        if (o instanceof Integer)
            return (Integer) o;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean toBool(Object o, boolean def) {
        if (o instanceof Boolean)
            return (Boolean) o;
        if (o == null)
            return def;
        String s = String.valueOf(o);
        if ("1".equals(s))
            return true;
        if ("0".equals(s))
            return false;
        try {
            return Boolean.parseBoolean(s);
        } catch (Exception e) {
            return def;
        }
    }

    private String safeJsonSnippet(JSONObject obj) {
        try {
            String s = obj == null ? "null" : obj.toString();
            if (s.length() > 500)
                return s.substring(0, 500) + "...";
            return s;
        } catch (Exception e) {
            return "json_snippet_error";
        }
    }

    private String buildVolleyError(VolleyError err) {
        if (err == null)
            return "VolleyError=null";

        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(err.getClass().getSimpleName());

        if (err.getCause() != null) {
            sb.append(" cause=").append(err.getCause().getClass().getSimpleName())
                    .append(":").append(err.getCause().getMessage());
        }

        try {
            if (err.networkResponse != null) {
                sb.append(" status=").append(err.networkResponse.statusCode);
                if (err.networkResponse.data != null) {
                    String body = new String(err.networkResponse.data);
                    body = body.trim();
                    if (body.length() > 300)
                        body = body.substring(0, 300) + "...";
                    sb.append(" body=").append(body);
                }
            } else {
                sb.append(" networkResponse=null");
            }
        } catch (Exception ignored) {
        }

        return sb.toString();
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        i.putExtra(Intent.EXTRA_TEXT,
                "Check out SheharSetu: https://play.google.com/store/apps/details?id=" + getPackageName());
        try {
            startActivity(Intent.createChooser(i, I18n.t(this, "Share via")));
        } catch (Exception e) {
            makeText(this, I18n.t(this, "No app found to share"), Toast.LENGTH_SHORT).show();
        }
    }

    private void rateUs() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    /**
     * Fetch logged-in user profile from API and update UI
     * Network optimized with caching and proper error handling
     */
    private void fetchUserProfile(TextView tvUserName, TextView tvUserPhone) {
        Log.d(TAG, "========== FETCH USER PROFILE START ==========");

        String accessToken = session.getAccessToken();
        Log.d(TAG, "Access Token: " + (accessToken != null ? "EXISTS (length=" + accessToken.length() + ")" : "NULL"));

        if (TextUtils.isEmpty(accessToken)) {
            Log.w(TAG, "❌ No access token found - showing Guest User");
            if (tvUserName != null)
                tvUserName.setText("Guest User");
            if (tvUserPhone != null)
                tvUserPhone.setText("");
            return;
        }

        String url = ApiRoutes.BASE_URL + "/get_user_profile.php";
        Log.d(TAG, "API URL: " + url);
        Log.d(TAG, "Making GET request to fetch user profile...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(TAG, "✅ API Response received");
                    Log.d(TAG, "Response: " + response.toString());
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            String name = user.optString("name", "User");
                            String formattedPhone = user.optString("formatted_phone", "");

                            Log.d(TAG, "User Name: " + name);
                            Log.d(TAG, "User Phone: " + formattedPhone);

                            if (tvUserName != null) {
                                tvUserName.setText(name);
                                Log.d(TAG, "✅ Updated tvUserName to: " + name);
                            }
                            if (tvUserPhone != null) {
                                tvUserPhone.setText(formattedPhone);
                                Log.d(TAG, "✅ Updated tvUserPhone to: " + formattedPhone);
                            }

                            Log.d(TAG, "✅ User profile loaded successfully");
                        } else {
                            String error = response.optString("error", "Unknown error");
                            Log.w(TAG, "❌ API returned success=false. Error: " + error);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing user profile response");
                        Log.e(TAG, "Exception: " + e.getMessage());
                        e.printStackTrace();
                    }
                    Log.d(TAG, "========== FETCH USER PROFILE COMPLETE ==========");
                },
                error -> {
                    Log.e(TAG, "❌ ========== USER PROFILE API ERROR ==========");
                    Log.e(TAG, "Error: " + error.toString());
                    if (error.networkResponse != null) {
                        Log.e(TAG, "Status Code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "Response Data: " + new String(error.networkResponse.data));
                    } else {
                        Log.e(TAG, "Network Response: NULL (likely network/SSL error)");
                    }
                    Log.e(TAG, "Cause: " + (error.getCause() != null ? error.getCause().getMessage() : "Unknown"));

                    if (tvUserName != null) {
                        tvUserName.setText("User");
                        Log.d(TAG, "Set fallback: User");
                    }
                    if (tvUserPhone != null) {
                        tvUserPhone.setText("");
                        Log.d(TAG, "Set fallback: empty phone");
                    }
                    Log.e(TAG, "========== USER PROFILE ERROR END ==========");
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                Log.d(TAG, "Request Headers: Authorization=Bearer [TOKEN], Content-Type=application/json");
                return headers;
            }
        };

        // Disable caching - user profile should always be fresh
        req.setShouldCache(false);

        // Network optimization: shorter timeout, no retries for profile fetch
        req.setRetryPolicy(new DefaultRetryPolicy(
                5000, // 5 second timeout
                0, // No retries - fail fast
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Log.d(TAG, "Adding request to Volley queue...");
        VolleySingleton.getInstance(this).add(req);
        Log.d(TAG, "Request added to queue. Waiting for response...");
    }

    /**
     * Fetch user profile on app startup and cache the data
     * This runs in background when MainActivity loads
     */
    private void fetchUserProfileOnStartup() {
        Log.d(TAG, "========== FETCH USER PROFILE ON STARTUP ==========");

        String accessToken = session.getAccessToken();
        Log.d(TAG, "Access Token: " + (accessToken != null ? "EXISTS (length=" + accessToken.length() + ")" : "NULL"));

        if (TextUtils.isEmpty(accessToken)) {
            Log.w(TAG, "❌ No access token found - using default values");
            cachedUserName = "Guest User";
            cachedUserPhone = "";
            return;
        }

        String url = ApiRoutes.BASE_URL + "/get_user_profile.php";
        Log.d(TAG, "API URL: " + url);
        Log.d(TAG, "Making GET request to fetch user profile on startup...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(TAG, "✅ API Response received on startup");
                    Log.d(TAG, "Response: " + response.toString());
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            cachedUserName = user.optString("name", "User");
                            cachedUserPhone = user.optString("formatted_phone", "");

                            Log.d(TAG, "✅ Cached User Name: " + cachedUserName);
                            Log.d(TAG, "✅ Cached User Phone: " + cachedUserPhone);

                            // Update TextViews immediately on UI thread
                            runOnUiThread(() -> {
                                if (tvNavUserName != null) {
                                    tvNavUserName.setText(cachedUserName);
                                    Log.d(TAG, "✅ Updated tvNavUserName to: " + cachedUserName);
                                }
                                if (tvNavUserPhone != null) {
                                    tvNavUserPhone.setText(cachedUserPhone);
                                    Log.d(TAG, "✅ Updated tvNavUserPhone to: " + cachedUserPhone);
                                }
                            });

                            Log.d(TAG, "✅ User profile cached and displayed successfully");
                        } else {
                            String error = response.optString("error", "Unknown error");
                            Log.w(TAG, "❌ API returned success=false. Error: " + error);
                            cachedUserName = "User";
                            cachedUserPhone = "";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing user profile response");
                        Log.e(TAG, "Exception: " + e.getMessage());
                        e.printStackTrace();
                        cachedUserName = "User";
                        cachedUserPhone = "";
                    }
                    Log.d(TAG, "========== FETCH USER PROFILE ON STARTUP COMPLETE ==========");
                },
                error -> {
                    Log.e(TAG, "❌ ========== USER PROFILE API ERROR ON STARTUP ==========");
                    Log.e(TAG, "Error: " + error.toString());
                    if (error.networkResponse != null) {
                        Log.e(TAG, "Status Code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "Response Data: " + new String(error.networkResponse.data));
                    } else {
                        Log.e(TAG, "Network Response: NULL (likely network/SSL error)");
                    }
                    Log.e(TAG, "Cause: " + (error.getCause() != null ? error.getCause().getMessage() : "Unknown"));

                    // Set fallback values
                    cachedUserName = "User";
                    cachedUserPhone = "";
                    Log.d(TAG, "Set fallback cached values");
                    Log.e(TAG, "========== USER PROFILE ERROR ON STARTUP END ==========");
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                Log.d(TAG, "Request Headers: Authorization=Bearer [TOKEN], Content-Type=application/json");
                return headers;
            }
        };

        // Disable caching - user profile should always be fresh
        req.setShouldCache(false);

        // Network optimization: shorter timeout, no retries
        req.setRetryPolicy(new DefaultRetryPolicy(
                5000, // 5 second timeout
                0, // No retries - fail fast
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Log.d(TAG, "Adding request to Volley queue (background fetch)...");
        VolleySingleton.getInstance(this).add(req);
        Log.d(TAG, "Background request added to queue");
    }
}
