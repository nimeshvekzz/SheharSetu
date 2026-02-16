package com.infowave.sheharsetu;

import static android.widget.Toast.makeText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    private static final String TAG_FETCH_PRODUCTS = "TAG_FETCH_PRODUCTS"; // Tag for cancelling requests

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
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private View layoutEmptyState;
    private android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;

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

    // ===== KM Filter State =====
    private Integer selectedRadiusKm = null; // null = no distance filter
    private Double userLat = null;
    private Double userLng = null;
    private Chip chipKmFilter;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> locationSettingsLauncher;

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

    // Initialize cached values from session on startup
    private void initCachedUserData() {
        cachedUserName = session.getCachedUserName();
        cachedUserPhone = session.getCachedUserPhone();
    }

    // ===== Pagination =====
    private int currentPage = 1;
    private static final int LIMIT = 20;
    private boolean hasMoreProducts = true;
    private boolean isLoadingMore = false;

    // ✅ Network optimization + correctness
    private android.util.LruCache<String, JSONObject> productCache;
    private String lastProductsUrl = null;
    private boolean productsInFlight = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply saved locale first
        applySavedLocale();
        session = new SessionManager(this);

        // Cache: 20 responses max
        productCache = new android.util.LruCache<>(20);

        // Initialize cached user data from session immediately
        initCachedUserData();

        // Register location settings launcher (must be before setContentView)
        locationSettingsLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // User enabled GPS → retry location fetch
                        doFetchLocation();
                    } else {
                        // User declined → reset filter
                        makeText(this, "GPS is required for distance filter", Toast.LENGTH_SHORT).show();
                        selectedRadiusKm = null;
                        updateKmChipText();
                    }
                });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
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

        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(() -> {
                // Clear cache completely to force fresh fetch from server
                productCache.evictAll();
                lastProductsUrl = null;
                productsInFlight = false;
                fetchProducts();
            });
        }

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
        GridLayoutManager productLayoutManager = new GridLayoutManager(this, 2);
        rvProducts.setLayoutManager(productLayoutManager);

        // ===== Infinite Scroll Listener =====
        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0)
                    return; // Only on scroll down
                int totalItems = productLayoutManager.getItemCount();
                int lastVisible = productLayoutManager.findLastVisibleItemPosition();
                if (!isLoadingMore && hasMoreProducts && lastVisible >= totalItems - 4) {
                    loadMoreProducts();
                }
            }
        });

        setupAdapters();

        btnPost.setOnClickListener(v -> startActivity(new Intent(this, CategorySelectActivity.class)));
        btnHelp.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));

        // ===== KM Filter Chip =====
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        chipKmFilter = findViewById(R.id.chipKmFilter);
        if (chipKmFilter != null) {
            chipKmFilter.setOnClickListener(v -> showKmFilterSheet());
        }

        prefetchAndApplyStaticTexts();

        // Load data
        showProducts();
        LoadingDialog.showLoading(this, "Loading data...");
        fetchCategories();
        fetchProducts(); // featured on first load
        fetchUserProfileOnStartup(); // Fetch user profile immediately on app start

        toggleNewOld.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked)
                return;
            ensureProductsView();
            showNew = (id == R.id.btnShowNew) ? Boolean.TRUE : Boolean.FALSE;
            resetPagination();
            fetchProducts();
        });
    }

    // ================= ✅ NEW HELPER METHODS FOR IMAGE FIXES =================

    /**
     * ✅ NEW: Helper to remove leading slashes from string
     */
    private static String ltrim(String str, char ch) {
        if (str == null || str.isEmpty())
            return str;
        while (str.length() > 0 && str.charAt(0) == ch) {
            str = str.substring(1);
        }
        return str;
    }

    private String makeAbsoluteImageUrl(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return "";
        }

        // Already absolute URL - return as-is
        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }

        // Relative path - convert to absolute
        String cleanPath = ltrim(imagePath, '/');
        return ApiRoutes.BASE_URL + "/" + cleanPath;
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
        if (searchHandler != null) {
            if (searchRunnable != null)
                searchHandler.removeCallbacks(searchRunnable);
        }

        // Show search progress indicator
        if (tiSearch != null) {
            tiSearch.setHelperText("Searching...");
            tiSearch.setHelperTextEnabled(true);
        }

        searchRunnable = () -> {
            if (tiSearch != null)
                tiSearch.setHelperTextEnabled(false);
            ensureProductsView();
            searchQuery = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.ROOT).trim();
            resetPagination();
            fetchProducts();
        };
        // Debounce: 500ms delay
        searchHandler.postDelayed(searchRunnable, 500);
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
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
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        if (drawerLayout == null || navigationView == null) {
            return;
        }

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        View header = navigationView.getHeaderView(0);
        if (header != null) {
            ImageView ivProfile = header.findViewById(R.id.ivProfile);
            tvNavUserName = header.findViewById(R.id.tvUserName);
            tvNavUserPhone = header.findViewById(R.id.tvUserPhone);
            ImageView ivEdit = header.findViewById(R.id.ivEdit);
            // Display cached user data (will be updated when API response arrives)
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

        // ✅ FIX: Ensure icons always show their original colors (not tinted grey)
        navigationView.setItemIconTintList(null);

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
            if (toggleNewOld != null) {
                toggleNewOld.setVisibility(View.GONE);
                toggleNewOld.clearChecked();
            }

            // Allow products to refetch after category change
            resetPagination();
            if (selectedCategoryId == 0) {
                // "All Listings" -> Skip subfilter fetch (client side optimization)
                mapSubFilters.remove(0); // clear if any
                showProducts();
                fetchProducts();
            } else {
                fetchSubFilters(selectedCategoryId);
            }
        });
        rvCategories.setAdapter(catAdapter);

        productAdapterRef = new ProductAdapter(this);
        rvProducts.setAdapter(productAdapterRef);
    }

    private void bindProducts(List<Map<String, Object>> items) {
        // Stop refresh animation
        if (swipeRefresh != null)
            swipeRefresh.setRefreshing(false);

        if (items == null || items.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            if (layoutEmptyState != null)
                layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            if (layoutEmptyState != null)
                layoutEmptyState.setVisibility(View.GONE);
            if (productAdapterRef != null)
                productAdapterRef.setItems(items);

            // Replay layout animation for smooth card transitions
            runLayoutAnimation();
        }
    }

    /** Replay the fall-down animation on the product grid */
    private void runLayoutAnimation() {
        if (rvProducts == null)
            return;
        final LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(this,
                R.anim.layout_animation_fall_down);
        rvProducts.setLayoutAnimation(controller);
        rvProducts.scheduleLayoutAnimation();
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
                .append("/list_products.php?page=").append(currentPage)
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

        // KM distance filter
        if (selectedRadiusKm != null && userLat != null && userLng != null) {
            sb.append("&lat=").append(userLat)
                    .append("&lng=").append(userLng)
                    .append("&radius=").append(selectedRadiusKm);
        }

        return sb.toString();
    }

    /** Reset pagination state when filters/search change */
    private void resetPagination() {
        currentPage = 1;
        hasMoreProducts = true;
        isLoadingMore = false;
        productsInFlight = false;
        lastProductsUrl = null;
    }

    /** Load next page of products (infinite scroll) */
    private void loadMoreProducts() {
        if (isLoadingMore || !hasMoreProducts)
            return;
        isLoadingMore = true;
        currentPage++;
        fetchProducts();
    }

    // ================= Network: Fetchers =================

    private void fetchCategories() {
        final String url = urlCategories();
        Log.e(TAG, "========== FETCH CATEGORIES START =========="); // Changed to Log.e for visibility
        Log.e(TAG, "URL: " + url);

        @SuppressLint("NotifyDataSetChanged")
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
                    Log.e(TAG, "✅ Categories Response: " + resp.toString());
                    try {
                        if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                            Log.e(TAG, "❌ Status is not success: " + resp.optString("status"));
                            makeText(this, I18n.t(this, "Categories error"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray arr = resp.optJSONArray("data");
                        Log.e(TAG, "Categories count: " + (arr != null ? arr.length() : 0));

                        categories.clear();

                        // ✅ ADDED: "All Listings" button at the start
                        Map<String, Object> allCat = new HashMap<>();
                        allCat.put("id", 0); // Special ID for "All"
                        allCat.put("name", "All Listings");
                        allCat.put("iconRes", R.drawable.ic_all_listings); // Custom dashboard/grid icon
                        categories.add(allCat);

                        List<String> catNameKeys = new ArrayList<>();
                        catNameKeys.add("All Listings");

                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                int catId = o.optInt("id", 0);
                                m.put("id", catId);
                                String nameEn = o.optString("name", "");
                                m.put("name", nameEn);

                                // ✅ FIXED: Proper icon URL handling with makeAbsoluteImageUrl
                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl))
                                    iconUrl = o.optString("icon_url", "");

                                // ✅ Convert relative path to absolute URL
                                iconUrl = makeAbsoluteImageUrl(iconUrl);
                                m.put("iconUrl", iconUrl);
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                categories.add(m);

                                if (!TextUtils.isEmpty(nameEn))
                                    catNameKeys.add(nameEn);
                            }
                        } else {
                            Log.e(TAG, "fetchCategories(): data array is NULL!");
                        }
                        catAdapter.notifyDataSetChanged();
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
                    Log.e(TAG, "❌ Categories Fetch Error: " + err.toString());
                    if (err.networkResponse != null) {
                        Log.e(TAG, "Status Code: " + err.networkResponse.statusCode);
                        Log.e(TAG, "Body: " + new String(err.networkResponse.data));
                    }
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

        // Extended Timeout - 15 seconds
        req.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        req.setShouldCache(false);
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchSubFilters(int categoryId) {
        final String url = urlSubcategories(categoryId);
        // ✅ CHECK MEMORY CACHE
        if (mapSubFilters.containsKey(categoryId)) {
            List<Map<String, Object>> subs = mapSubFilters.get(categoryId);

            rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
                selectedSubFilterId = toInt(sub.get("id"), 0);
                clearSearch();
                showProducts();

                if (selectedSubFilterId > 0) {
                    boolean hasNewOld = toBool(sub.get("hasNewOld"), false);
                    if (hasNewOld && toggleNewOld != null)
                        toggleNewOld.setVisibility(View.VISIBLE);
                    else if (toggleNewOld != null) {
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
            return; // SKIP API
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
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
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                Map<String, Object> m = new HashMap<>();
                                int subId = o.optInt("id", 0);
                                m.put("id", subId);
                                m.put("category_id", o.optInt("category_id", categoryId));
                                String subName = o.optString("name", "");
                                m.put("name", subName);

                                // ✅ FIXED: Proper icon URL handling with makeAbsoluteImageUrl
                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl))
                                    iconUrl = o.optString("icon_url", "");

                                // ✅ Convert relative path to absolute URL
                                iconUrl = makeAbsoluteImageUrl(iconUrl);
                                m.put("iconUrl", iconUrl);
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                subs.add(m);
                            }
                        } else {
                            Log.e(TAG, "fetchSubFilters(): data array is NULL!");
                        }
                        mapSubFilters.put(categoryId, subs);

                        rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
                            selectedSubFilterId = toInt(sub.get("id"), 0);
                            clearSearch();
                            showProducts();
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
        final boolean isAppend = currentPage > 1; // Loading more pages

        // Cancel previous requests only if this is a fresh load (not append)
        if (!isAppend && productsInFlight) {
            VolleySingleton.getInstance(this).getQueue().cancelAll(TAG_FETCH_PRODUCTS);
        }
        productsInFlight = true;

        // Check memory cache (only for page 1)
        if (!isAppend) {
            JSONObject cachedResp = productCache.get(url);
            if (cachedResp != null) {
                try {
                    productsInFlight = false;
                    if (swipeRefresh != null)
                        swipeRefresh.setRefreshing(false);
                    parseProductsResponse(cachedResp, false);
                    lastProductsUrl = url;
                    return;
                } catch (Exception e) {
                    // fallback to network
                }
            }
        }

        lastProductsUrl = url;

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                resp -> {
                    productsInFlight = false;
                    isLoadingMore = false;

                    if (swipeRefresh != null)
                        swipeRefresh.setRefreshing(false);

                    if (resp != null) {
                        if (!isAppend)
                            productCache.put(url, resp);
                        parseProductsResponse(resp, isAppend);
                    }
                },
                err -> {
                    Log.e(TAG, "fetchProducts() ERROR: " + err.toString());
                    productsInFlight = false;
                    isLoadingMore = false;

                    if (swipeRefresh != null)
                        swipeRefresh.setRefreshing(false);

                    makeText(this, I18n.t(this, "Network error (products)"), Toast.LENGTH_SHORT).show();

                    if (currentProducts.isEmpty()) {
                        bindProducts(new ArrayList<>());
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Accept-Language", I18n.lang(MainActivity.this));
                return headers;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        req.setShouldCache(false);
        req.setTag(TAG_FETCH_PRODUCTS);
        VolleySingleton.getInstance(this).add(req);
    }

    // ================= KM Filter Bottom Sheet =================

    private void showKmFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_km_filter, null);
        dialog.setContentView(sheet);

        ChipGroup chipGroup = sheet.findViewById(R.id.chipGroupKm);

        // Pre-select current value
        if (selectedRadiusKm == null) {
            ((Chip) sheet.findViewById(R.id.chipAll)).setChecked(true);
        } else if (selectedRadiusKm == 5) {
            ((Chip) sheet.findViewById(R.id.chip5km)).setChecked(true);
        } else if (selectedRadiusKm == 10) {
            ((Chip) sheet.findViewById(R.id.chip10km)).setChecked(true);
        } else if (selectedRadiusKm == 25) {
            ((Chip) sheet.findViewById(R.id.chip25km)).setChecked(true);
        } else if (selectedRadiusKm == 50) {
            ((Chip) sheet.findViewById(R.id.chip50km)).setChecked(true);
        } else if (selectedRadiusKm == 100) {
            ((Chip) sheet.findViewById(R.id.chip100km)).setChecked(true);
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty())
                return;
            int checkedId = checkedIds.get(0);

            if (checkedId == R.id.chipAll) {
                selectedRadiusKm = null;
                updateKmChipText();
                dialog.dismiss();
                applyKmFilter();
            } else {
                int km = 10; // default
                if (checkedId == R.id.chip5km)
                    km = 5;
                else if (checkedId == R.id.chip10km)
                    km = 10;
                else if (checkedId == R.id.chip25km)
                    km = 25;
                else if (checkedId == R.id.chip50km)
                    km = 50;
                else if (checkedId == R.id.chip100km)
                    km = 100;

                selectedRadiusKm = km;
                updateKmChipText();
                dialog.dismiss();

                // Need user location to filter by radius
                if (userLat == null || userLng == null) {
                    fetchUserLocationThenFilter();
                } else {
                    applyKmFilter();
                }
            }
        });

        dialog.show();
    }

    private void updateKmChipText() {
        if (chipKmFilter == null)
            return;
        if (selectedRadiusKm == null) {
            chipKmFilter.setText("📍 Nearby");
        } else {
            chipKmFilter.setText("📍 " + selectedRadiusKm + " km");
        }
    }

    private void applyKmFilter() {
        ensureProductsView();
        // Clear cache so new radius is applied
        productCache.evictAll();
        lastProductsUrl = null;
        productsInFlight = false;
        fetchProducts();
    }

    @SuppressLint("MissingPermission")
    private void fetchUserLocationThenFilter() {
        // 1. Check permission first
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        // 2. Check if GPS/Location services are enabled
        com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest
                .create()
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);

        com.google.android.gms.location.LocationSettingsRequest settingsRequest = new com.google.android.gms.location.LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true) // Show dialog even if GPS was previously declined
                .build();

        com.google.android.gms.location.LocationServices.getSettingsClient(this)
                .checkLocationSettings(settingsRequest)
                .addOnSuccessListener(this, response -> {
                    // GPS is ON — go ahead and fetch location
                    doFetchLocation();
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof com.google.android.gms.common.api.ResolvableApiException) {
                        // GPS is OFF — show Google's "Enable Location" dialog
                        try {
                            com.google.android.gms.common.api.ResolvableApiException resolvable = (com.google.android.gms.common.api.ResolvableApiException) e;
                            locationSettingsLauncher.launch(
                                    new androidx.activity.result.IntentSenderRequest.Builder(
                                            resolvable.getResolution()).build());
                        } catch (Exception ex) {
                            Log.e(TAG, "Could not show location settings dialog", ex);
                            showManualGpsPrompt();
                        }
                    } else {
                        // Some other settings error
                        showManualGpsPrompt();
                    }
                });
    }

    /** Fetch location after GPS check passes */
    @SuppressLint("MissingPermission")
    private void doFetchLocation() {
        LoadingDialog.showLoading(this, "Getting your location...");
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    LoadingDialog.hideLoading();
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLng = location.getLongitude();
                        Log.d(TAG, "User location: " + userLat + ", " + userLng);
                        applyKmFilter();
                    } else {
                        // Last location is null — request a fresh one
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(this, e -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "Location fetch failed", e);
                    makeText(this, "Location error. Please try again.", Toast.LENGTH_SHORT).show();
                    selectedRadiusKm = null;
                    updateKmChipText();
                });
    }

    /** Request a fresh location when getLastLocation returns null */
    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        com.google.android.gms.location.LocationRequest req = com.google.android.gms.location.LocationRequest.create()
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setInterval(5000)
                .setMaxWaitTime(10000);

        fusedLocationClient.requestLocationUpdates(req,
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(com.google.android.gms.location.LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);
                        LoadingDialog.hideLoading();
                        if (result != null && result.getLastLocation() != null) {
                            userLat = result.getLastLocation().getLatitude();
                            userLng = result.getLastLocation().getLongitude();
                            Log.d(TAG, "Fresh location: " + userLat + ", " + userLng);
                            applyKmFilter();
                        } else {
                            makeText(MainActivity.this, "Could not get location. Try again.", Toast.LENGTH_SHORT)
                                    .show();
                            selectedRadiusKm = null;
                            updateKmChipText();
                        }
                    }
                }, android.os.Looper.getMainLooper());
    }

    /** Fallback: prompt user to open GPS settings manually */
    private void showManualGpsPrompt() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Enable Location")
                .setMessage("GPS is turned off. Please enable location services to filter listings by distance.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    selectedRadiusKm = null;
                    updateKmChipText();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, retry location fetch
                fetchUserLocationThenFilter();
            } else {
                makeText(this, "Location permission needed for distance filter", Toast.LENGTH_SHORT).show();
                selectedRadiusKm = null;
                updateKmChipText();
            }
        }
    }

    /**
     * Helper to parse product response - logic extracted for reuse
     */
    private void parseProductsResponse(JSONObject resp, boolean isAppend) {
        try {
            if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                Log.e(TAG, "parseProductsResponse(): status != success");
                makeText(this, I18n.t(this, "Products error"), Toast.LENGTH_SHORT).show();
                if (!isAppend)
                    bindProducts(new ArrayList<>());
                return;
            }

            // Read pagination info from response
            hasMoreProducts = resp.optBoolean("has_more", false);

            JSONArray arr = resp.optJSONArray("data");
            List<Map<String, Object>> newItems = new ArrayList<>();

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Map<String, Object> m = new HashMap<>();
                    int prodId = o.optInt("id", 0);
                    m.put("id", prodId);
                    m.put("categoryId", o.optInt("category_id", 0));
                    m.put("subFilterId", o.optInt("subcategory_id", 0));
                    m.put("title", o.optString("title", ""));
                    m.put("price", String.valueOf(o.opt("price")));
                    m.put("city", o.optString("city", ""));

                    String imageUrl = "";
                    if (!o.isNull("cover_image"))
                        imageUrl = o.optString("cover_image", "");
                    if (TextUtils.isEmpty(imageUrl) && !o.isNull("image_url"))
                        imageUrl = o.optString("image_url", "");
                    if (TextUtils.isEmpty(imageUrl) && !o.isNull("image"))
                        imageUrl = o.optString("image", "");

                    m.put("imageUrl", makeAbsoluteImageUrl(imageUrl));
                    m.put("isNew", o.optInt("is_new", 0) == 1);

                    String posted = o.optString("posted_when", "");
                    if (TextUtils.isEmpty(posted)) {
                        posted = o.optString("posted_time", "");
                    }
                    m.put("posted_when", posted);

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
                    if (images.isEmpty() && !TextUtils.isEmpty(m.get("imageUrl").toString())) {
                        images.add(m.get("imageUrl").toString());
                    }
                    m.put("images", images);

                    newItems.add(m);
                }
            }

            if (isAppend) {
                // Infinite scroll: append to existing list
                currentProducts.addAll(newItems);
                if (productAdapterRef != null) {
                    productAdapterRef.addItems(newItems);
                }
            } else {
                // Fresh load: replace entire list
                currentProducts.clear();
                currentProducts.addAll(newItems);
                bindProducts(new ArrayList<>(currentProducts));
            }
            LoadingDialog.hideLoading();

        } catch (Exception e) {
            Log.e(TAG, "parseProductsResponse(): exception", e);
            if (!isAppend)
                bindProducts(new ArrayList<>());
        }
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
     * ✅ KEPT: Original method - Fetch logged-in user profile from API and update UI
     * Network optimized with caching and proper error handling
     */
    @SuppressLint("SetTextI18n")
    private void fetchUserProfile(TextView tvUserName, TextView tvUserPhone) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            if (tvUserName != null)
                tvUserName.setText("Guest User");
            if (tvUserPhone != null)
                tvUserPhone.setText("");
            return;
        }

        String url = ApiRoutes.BASE_URL + "/get_user_profile.php";
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            String name = user.optString("name", "User");
                            String formattedPhone = user.optString("formatted_phone", "");
                            if (tvUserName != null) {
                                tvUserName.setText(name);
                            }
                            if (tvUserPhone != null) {
                                tvUserPhone.setText(formattedPhone);
                            }
                        } else {
                            String error = response.optString("error", "Unknown error");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing user profile response");
                        Log.e(TAG, "Exception: " + e.getMessage());
                        e.printStackTrace();
                    }
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
                    }
                    if (tvUserPhone != null) {
                        tvUserPhone.setText("");
                    }
                    Log.e(TAG, "========== USER PROFILE ERROR END ==========");
                }) {

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }

        };

        // Disable caching - user profile should always be fresh
        req.setShouldCache(false);

        // Network optimization: shorter timeout, no retries for profile fetch
        req.setRetryPolicy(new DefaultRetryPolicy(5000, // 5 second timeout
                0, // No retries - fail fast
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchUserProfileOnStartup() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            cachedUserName = "Guest User";
            cachedUserPhone = "";
            return;
        }

        String url = ApiRoutes.BASE_URL + "/get_user_profile.php";
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            cachedUserName = user.optString("name", "User");
                            cachedUserPhone = user.optString("formatted_phone", "");
                            // Save to session for next startup
                            session.saveUserProfile(cachedUserName, cachedUserPhone);
                            // Update TextViews immediately on UI thread
                            runOnUiThread(() -> {
                                if (tvNavUserName != null) {
                                    tvNavUserName.setText(cachedUserName);
                                }
                                if (tvNavUserPhone != null) {
                                    tvNavUserPhone.setText(cachedUserPhone);
                                }
                            });
                        } else {
                            String error = response.optString("error", "Unknown error");
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
                    Log.e(TAG, "========== USER PROFILE ERROR ON STARTUP END ==========");
                }) {

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        // Disable caching - user profile should always be fresh
        req.setShouldCache(false);

        // Network optimization: 10 second timeout with 1 retry
        req.setRetryPolicy(new DefaultRetryPolicy(10000, // 10 second timeout
                1, // 1 retry
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }
}