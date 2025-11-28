package com.infowave.sheharsetu;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
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
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

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
    private int selectedSubFilterId = -1;   // -1 = none (sub grid hidden), 0 = ALL
    private Boolean showNew = null;         // null = all, true=new, false=old
    private String searchQuery = "";

    // ===== Adapters =====
    private CategoryAdapter catAdapter;
    private ProductAdapter productAdapterRef;

    // ===== Locale Prefs =====
    private static final String PREFS    = LanguageSelection.PREFS;          // "sheharsetu_prefs"
    private static final String KEY_LANG = LanguageSelection.KEY_LANG_CODE;  // "app_lang_code";

    // ===== Session =====
    private SessionManager session;

    // ===== Network =====
    private RequestQueue queue;
    private static final int PAGE = 1;
    private static final int LIMIT = 50;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply saved locale first
        applySavedLocale();
        session = new SessionManager(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return insets;
        });

        queue = Volley.newRequestQueue(this);

        bindHeader();

        rvCategories     = findViewById(R.id.rvCategories);
        rvSubFiltersGrid = findViewById(R.id.rvSubFiltersGrid);
        rvProducts       = findViewById(R.id.rvProducts);
        toggleNewOld     = findViewById(R.id.toggleNewOld);
        tvSectionTitle   = findViewById(R.id.tvSectionTitle);

        btnPost   = findViewById(R.id.btnPost);
        btnHelp   = findViewById(R.id.btnHelp);
        tvMarquee = findViewById(R.id.tvMarquee);
        tvMarquee.setSelected(true);

        TextView tvLangBadge = findViewById(R.id.tvLangBadge);
        if (tvLangBadge != null) {
            tvLangBadge.setText(
                    I18n.t(this, "Language") + ": " + session.getLangName()
            );
        }

        setupVoiceLauncher();
        setupSearch();
        setupLanguageToggle();
        setupAppDrawer();

        rvCategories.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        rvSubFiltersGrid.setLayoutManager(new GridLayoutManager(this, 3));
        rvProducts.setLayoutManager(new GridLayoutManager(this, 2));

        setupAdapters();

        btnPost.setOnClickListener(v -> startActivity(new Intent(this, CategorySelectActivity.class)));
        btnHelp.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));

        prefetchAndApplyStaticTexts();

        // Load data
        showProducts();
        fetchCategories();
        fetchProducts(); // featured on first load

        toggleNewOld.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            ensureProductsView();
            showNew = (id == R.id.btnShowNew) ? Boolean.TRUE : Boolean.FALSE;
            fetchProducts();
        });
    }

    // ================= Header/Search/Voice =================

    private void bindHeader() {
        btnDrawer = findViewById(R.id.btnDrawer);
        tiSearch  = findViewById(R.id.tiSearch);
        etSearch  = findViewById(R.id.etSearch);
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
        tiSearch.setEndIconOnClickListener(v -> startVoiceInput());
    }

    private void setupSearch() {
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
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, I18n.t(this, "Speak to search…"));
        try {
            speechLauncher.launch(i);
        } catch (Exception e) {
            Toast.makeText(this, I18n.t(this, "Voice search not available"), Toast.LENGTH_SHORT).show();
        }
    }

    private void performSearch(String query) {
        ensureProductsView();
        searchQuery = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.ROOT).trim();
        fetchProducts();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
        LanguageManager.apply(this, lang);
    }

    private void setupLanguageToggle() {
        if (btnDrawer == null) return;

        // Tap opens drawer
        btnDrawer.setOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
        });

        // Long-press toggles EN <-> HI
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
            Toast.makeText(
                    this,
                    I18n.t(this, "Language") + ": " + nextName,
                    Toast.LENGTH_SHORT
            ).show();

            recreate();
            return true;
        });
    }

    // ================= Drawer =================

    private void setupAppDrawer() {
        drawerLayout   = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        if (drawerLayout == null || navigationView == null) return;

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // Setup header view (design only; you can later bind dynamic data here)
        View header = navigationView.getHeaderView(0);
        if (header != null) {
            ImageView ivProfile = header.findViewById(R.id.ivProfile);
            TextView tvUserName = header.findViewById(R.id.tvUserName);
            TextView tvUserPhone = header.findViewById(R.id.tvUserPhone);
            ImageView ivEdit = header.findViewById(R.id.ivEdit);

            // You can set user data here if needed:
            // tvUserName.setText(session.getUserName());
            // tvUserPhone.setText(session.getUserPhone());

            if (ivEdit != null) {
                ivEdit.setOnClickListener(v -> {
                    // TODO: open profile screen when ready
                    Toast.makeText(MainActivity.this,
                            I18n.t(this, "Profile coming soon"), Toast.LENGTH_SHORT).show();
                });
            }
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

            } else if (id == R.id.nav_whatsapp) {
                Toast.makeText(MainActivity.this, "WhatsApp", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_youtube) {
                Toast.makeText(MainActivity.this, "YouTube", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_weather) {
                Toast.makeText(MainActivity.this, "Weather", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_invite) {
                shareApp();

            } else if (id == R.id.nav_rate) {
                rateUs();

            } else if (id == R.id.nav_facebook) {
                Toast.makeText(MainActivity.this, "Facebook", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_contact) {
                Toast.makeText(MainActivity.this, "Contact us", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_terms) {
                Toast.makeText(MainActivity.this, "Terms & Conditions", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_about) {
                Toast.makeText(MainActivity.this, "About", Toast.LENGTH_SHORT).show();

            } else if (id == R.id.nav_logout) {
                // NEW: Logout handling
                doLogout();
            } else {
                Toast.makeText(MainActivity.this, I18n.t(this, "Coming soon"), Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void doLogout() {
        // Clear user-related data
        getSharedPreferences("user", MODE_PRIVATE).edit().clear().apply();

        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        // Go back to language selection or login screen
        Intent i = new Intent(this, LanguageSelection.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ================= Static UI translation =================

    private void prefetchAndApplyStaticTexts() {
        List<String> keys = new ArrayList<>();

        if (tiSearch != null && tiSearch.getHint() != null) {
            keys.add(tiSearch.getHint().toString());
        }
        if (tvSectionTitle != null && tvSectionTitle.getText() != null) {
            keys.add(tvSectionTitle.getText().toString());
        }
        if (tvMarquee != null && tvMarquee.getText() != null) {
            keys.add(tvMarquee.getText().toString());
        }

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
            if (tiSearch != null) {
                I18n.translateAndApplyHint(tiSearch, this);
            }
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

            boolean hasNewOld = toBool(cat.get("hasNewOld"), false);
            toggleNewOld.setVisibility(hasNewOld ? View.VISIBLE : View.GONE);
            toggleNewOld.clearChecked();

            fetchSubFilters(selectedCategoryId);
        });
        rvCategories.setAdapter(catAdapter);

        productAdapterRef = new ProductAdapter(this);
        rvProducts.setAdapter(productAdapterRef);
    }

    private void bindProducts(List<Map<String, Object>> items) {
        rvProducts.setVisibility(View.VISIBLE);
        if (productAdapterRef != null) productAdapterRef.setItems(items);
    }

    private void showSubFilters() {
        rvProducts.setVisibility(View.GONE);
        tvSectionTitle.setVisibility(View.GONE);
        rvSubFiltersGrid.setVisibility(View.VISIBLE);
    }

    private void showProducts() {
        rvSubFiltersGrid.setVisibility(View.GONE);
        tvSectionTitle.setVisibility(View.VISIBLE);
        rvProducts.setVisibility(View.VISIBLE);
        if (tvSectionTitle != null) {
            tvSectionTitle.setText(
                    I18n.t(this, getString(R.string.featured_listings))
            );
        }
    }

    private void ensureProductsView() {
        if (rvSubFiltersGrid.getVisibility() == View.VISIBLE) showProducts();
    }

    private void clearSearch() {
        searchQuery = "";
        if (etSearch != null && etSearch.getText() != null) etSearch.setText("");
    }

    // ================= Network: Fetchers =================

    private String urlCategories() {
        String base = ApiRoutes.BASE_URL;
        return base + "/list_categories.php";
    }

    private String urlSubcategories(int categoryId) {
        return ApiRoutes.BASE_URL + "/list_subcategories.php?category_id=" + categoryId;
    }

    private String urlProducts() {
        StringBuilder sb = new StringBuilder(ApiRoutes.BASE_URL)
                .append("/list_products.php?page=").append(PAGE)
                .append("&limit=").append(LIMIT)
                .append("&sort=newest");
        if (selectedCategoryId > 0) sb.append("&category_id=").append(selectedCategoryId);
        if (selectedSubFilterId > 0) sb.append("&subcategory_id=").append(selectedSubFilterId);
        if (!TextUtils.isEmpty(searchQuery)) sb.append("&q=").append(android.net.Uri.encode(searchQuery));
        if (showNew != null) sb.append("&is_new=").append(showNew ? "1" : "0");
        return sb.toString();
    }

    private void fetchCategories() {
        @SuppressLint("NotifyDataSetChanged") JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlCategories(),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, I18n.t(this, "Categories error"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        categories.clear();

                        List<String> catNameKeys = new ArrayList<>();

                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",   o.optInt("id", 0));
                                String nameEn = o.optString("name", "");
                                m.put("name", nameEn);

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl)) {
                                    iconUrl = o.optString("icon_url", "");
                                }
                                m.put("iconUrl", iconUrl);
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);
                                categories.add(m);

                                if (!TextUtils.isEmpty(nameEn)) {
                                    catNameKeys.add(nameEn);
                                }
                            }
                        }

                        catAdapter.notifyDataSetChanged();

                        I18n.prefetch(this, catNameKeys, () -> {
                            for (Map<String, Object> m : categories) {
                                Object nObj = m.get("name");
                                if (nObj != null) {
                                    String en = String.valueOf(nObj);
                                    String tr = I18n.t(this, en);
                                    m.put("name", tr);
                                }
                            }
                            catAdapter.notifyDataSetChanged();
                        });

                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Parse categories failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, I18n.t(this, "Network error (categories)"), Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
    }

    private void fetchSubFilters(int categoryId) {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlSubcategories(categoryId),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, I18n.t(this, "Subcategories error"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        List<Map<String, Object>> subs = new ArrayList<>();

                        Map<String, Object> all = new HashMap<>();
                        all.put("id", 0);
                        all.put("name", getString(R.string.sub_all));
                        all.put("iconRes", R.drawable.ic_placeholder_circle);
                        subs.add(all);

                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",          o.optInt("id", 0));
                                m.put("category_id", o.optInt("category_id", categoryId));
                                m.put("name",        o.optString("name", ""));

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl)) {
                                    iconUrl = o.optString("icon_url", "");
                                }
                                m.put("iconUrl", iconUrl);

                                subs.add(m);
                            }
                        }
                        mapSubFilters.put(categoryId, subs);

                        rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
                            selectedSubFilterId = toInt(sub.get("id"), 0);
                            clearSearch();
                            showProducts();
                            fetchProducts();
                        }));
                        showSubFilters();
                        catAdapter.setSelectedId(selectedCategoryId);
                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Parse subcategories failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, I18n.t(this, "Network error (subcategories)"), Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
    }

    private void fetchProducts() {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlProducts(),
                null,
                resp -> {
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Toast.makeText(this, I18n.t(this, "Products error"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = resp.optJSONArray("data");
                        currentProducts.clear();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                m.put("id",          o.optInt("id", 0));
                                m.put("categoryId",  o.optInt("category_id", 0));
                                m.put("subFilterId", o.optInt("subcategory_id", 0));
                                m.put("title",       o.optString("title", ""));

                                m.put("price",
                                        o.opt("price") == null
                                                ? ""
                                                : String.valueOf(o.opt("price")));

                                m.put("city", o.optString("city", ""));

                                String imageUrl = "";
                                if (!o.isNull("image_url")) {
                                    imageUrl = o.optString("image_url", "");
                                } else if (!o.isNull("image")) {
                                    imageUrl = o.optString("image", "");
                                }
                                m.put("imageUrl", imageUrl);

                                // ✅ Only 1 = NEW, everything else (0 / null / missing) = OLD
                                int rawIsNew = o.isNull("is_new")
                                        ? -1
                                        : o.optInt("is_new", -1);
                                boolean isNew = (rawIsNew == 1);
                                m.put("isNew", isNew);

                                currentProducts.add(m);
                            }
                        }
                        bindProducts(new ArrayList<>(currentProducts));
                        if (tvSectionTitle != null) {
                            tvSectionTitle.setText(
                                    I18n.t(this, getString(R.string.featured_listings))
                            );
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, I18n.t(this, "Parse products failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> Toast.makeText(this, I18n.t(this, "Network error (products)"), Toast.LENGTH_SHORT).show()
        );
        req.setRetryPolicy(new DefaultRetryPolicy(8000, 1, 1));
        queue.add(req);
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
            fetchProducts();
            return;
        }
        if (selectedCategoryId != -1) {
            selectedCategoryId = -1;
            selectedSubFilterId = -1;
            showNew = null;
            toggleNewOld.setVisibility(View.GONE);
            if (catAdapter != null) catAdapter.setSelectedId(-1);
            showProducts();
            clearSearch();
            fetchProducts();
            return;
        }
        super.onBackPressed();
    }

    // ================= Helpers =================

    private static int toInt(Object o, int def) {
        if (o instanceof Integer) return (Integer) o;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static boolean toBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o == null) return def;
        String s = String.valueOf(o);
        if ("1".equals(s)) return true;
        if ("0".equals(s)) return false;
        try { return Boolean.parseBoolean(s); } catch (Exception e) { return def; }
    }

    private void shareApp() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        i.putExtra(Intent.EXTRA_TEXT, "Check out SheharSetu: https://play.google.com/store/apps/details?id=" + getPackageName());
        try {
            startActivity(Intent.createChooser(i, I18n.t(this, "Share via")));
        } catch (Exception e) {
            Toast.makeText(this, I18n.t(this, "No app found to share"), Toast.LENGTH_SHORT).show();
        }
    }

    private void rateUs() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }
}
