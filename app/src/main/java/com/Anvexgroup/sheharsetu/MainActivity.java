package com.Anvexgroup.sheharsetu;

import static android.widget.Toast.makeText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
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
import androidx.core.view.WindowInsetsControllerCompat;
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
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

import com.Anvexgroup.sheharsetu.Adapter.CategoryAdapter;
import com.Anvexgroup.sheharsetu.Adapter.I18n;
import com.Anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.Anvexgroup.sheharsetu.Adapter.ProductAdapter;
import com.Anvexgroup.sheharsetu.Adapter.SubFilterGridAdapter;
import com.Anvexgroup.sheharsetu.core.SessionManager;
import com.Anvexgroup.sheharsetu.net.ApiRoutes;
import com.Anvexgroup.sheharsetu.net.VolleySingleton;
import com.Anvexgroup.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String TAG_FETCH_PRODUCTS = "TAG_FETCH_PRODUCTS";

    // ===== Views (Header) =====
    private ImageView btnDrawer, btnVoiceSearch;
    private TextInputEditText etSearch;
    private TextView tvSectionTitle;
    private TextView tvLocation;
    private ActivityResultLauncher<Intent> speechLauncher;

    // ===== Lists =====
    private RecyclerView rvCategories, rvSubFiltersGrid, rvProducts;
    private Chip chipCondition;

    // ===== Bottom banner =====
    private ImageButton btnPost, btnHelp;
    private TextView tvMarquee;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private View layoutEmptyState;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean ignoreSearchTextChanges = false;

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
    private int selectedSubFilterId = -1; // -1 = none, 0 = ALL
    private Boolean showNew = null; // null = all, true=new, false=old
    private String searchQuery = "";

    // ===== KM Filter State =====
    private Integer selectedRadiusKm = null;
    private Double userLat = null;
    private Double userLng = null;
    private Chip chipKmFilter;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int HEADER_LOCATION_PERMISSION_REQUEST = 1002;
    private ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> locationSettingsLauncher;

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
    private TextView tvNavUserName;
    private TextView tvNavUserPhone;

    // ===== Pagination =====
    private int currentPage = 1;
    private static final int LIMIT = 20;
    private boolean hasMoreProducts = true;
    private boolean isLoadingMore = false;

    // ===== Network correctness =====
    private android.util.LruCache<String, JSONObject> productCache;
    private String lastProductsUrl = null;
    private boolean productsInFlight = false;

    private void initCachedUserData() {
        cachedUserName = session.getCachedUserName();
        cachedUserPhone = session.getCachedUserPhone();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applySavedLocale();
        session = new SessionManager(this);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        productCache = new android.util.LruCache<>(20);
        initCachedUserData();

        locationSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        doFetchLocation();
                    } else {
                        makeText(this, I18n.t(this, "GPS is required for distance filter"), Toast.LENGTH_SHORT).show();
                        selectedRadiusKm = null;
                        updateKmChipText();
                    }
                });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
            windowInsetsController.setAppearanceLightNavigationBars(false);
        }

        setContentView(R.layout.activity_main);

        View viewStatusBarBackground = findViewById(R.id.viewStatusBarBackground);
        View viewNavBarBackground = findViewById(R.id.viewNavBarBackground);

        View rootContainer = findViewById(R.id.rootContainer);
        if (rootContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                if (viewStatusBarBackground != null) {
                    viewStatusBarBackground.getLayoutParams().height = systemBars.top;
                    viewStatusBarBackground.requestLayout();
                }

                if (viewNavBarBackground != null) {
                    viewNavBarBackground.getLayoutParams().height = systemBars.bottom;
                    viewNavBarBackground.requestLayout();
                }

                return insets;
            });
        }

        bindHeader();

        rvCategories = findViewById(R.id.rvCategories);
        rvSubFiltersGrid = findViewById(R.id.rvSubFiltersGrid);
        rvProducts = findViewById(R.id.rvProducts);
        chipCondition = findViewById(R.id.chipCondition);
        tvSectionTitle = findViewById(R.id.tvSectionTitle);

        if (chipCondition != null) {
            chipCondition.setVisibility(View.GONE);
            chipCondition.setOnClickListener(v -> showConditionPopup());
            showNew = null;
        }

        btnPost = findViewById(R.id.btnPost);
        btnHelp = findViewById(R.id.btnHelp);
        tvMarquee = findViewById(R.id.tvMarquee);
        if (tvMarquee != null) tvMarquee.setSelected(true);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
            swipeRefresh.setOnRefreshListener(() -> {
                productCache.evictAll();
                lastProductsUrl = null;
                productsInFlight = false;
                resetPagination();
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
        rvProducts.setLayoutManager(new GridLayoutManager(this, 2));

        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;

                RecyclerView.LayoutManager lm = rvProducts.getLayoutManager();
                if (lm == null) return;

                int totalItems = lm.getItemCount();
                int lastVisible = 0;

                if (lm instanceof GridLayoutManager) {
                    lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
                } else if (lm instanceof LinearLayoutManager) {
                    lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
                }

                if (!isLoadingMore && hasMoreProducts && lastVisible >= totalItems - 4) {
                    loadMoreProducts();
                }
            }
        });

        setupAdapters();

        if (btnPost != null) {
            btnPost.setOnClickListener(v ->
                    startActivity(new Intent(this, CategorySelectActivity.class)));
        }
        if (btnHelp != null) {
            btnHelp.setOnClickListener(v ->
                    startActivity(new Intent(this, HelpActivity.class)));
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        chipKmFilter = findViewById(R.id.chipKmFilter);
        if (chipKmFilter != null) {
            chipKmFilter.setOnClickListener(v -> showKmFilterSheet());
        }

        fetchAndDisplayHeaderLocation();

        prefetchAndApplyStaticTexts();

        showProducts();
        LoadingDialog.showLoading(this, "Loading data...");
        fetchCategories();
        fetchProducts();
        fetchUserProfileOnStartup();

        AppBarLayout appBarLayout = findViewById(R.id.appBarLayout);
        ImageView btnSearchSmall = findViewById(R.id.btnSearchSmall);
        View tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);

        if (appBarLayout != null && btnSearchSmall != null) {
            appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
                int scrollRange = appBarLayout1.getTotalScrollRange();
                if (scrollRange == 0) return;

                float fraction = (float) Math.abs(verticalOffset) / (float) scrollRange;

                if (fraction > 0.8f) {
                    float alpha = (fraction - 0.8f) * 5f;
                    btnSearchSmall.setVisibility(View.VISIBLE);
                    btnSearchSmall.setAlpha(alpha);
                    if (tvToolbarTitle != null) {
                        tvToolbarTitle.setVisibility(View.VISIBLE);
                        tvToolbarTitle.setAlpha(alpha);
                    }
                } else {
                    btnSearchSmall.setVisibility(View.GONE);
                    btnSearchSmall.setAlpha(0f);
                    if (tvToolbarTitle != null) {
                        tvToolbarTitle.setVisibility(View.GONE);
                        tvToolbarTitle.setAlpha(0f);
                    }
                }

                if (toolbar != null) {
                    if (fraction > 0.9f) {
                        toolbar.setBackgroundColor(
                                ContextCompat.getColor(MainActivity.this, R.color.colorPrimary));
                    } else {
                        toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    }
                }
            });

            btnSearchSmall.setOnClickListener(v -> {
                appBarLayout.setExpanded(true, true);
                if (etSearch != null) {
                    etSearch.requestFocus();
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager)
                                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etSearch,
                                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });
        }
    }

    // ================= Helpers =================

    private static String ltrim(String str, char ch) {
        if (str == null || str.isEmpty()) return str;
        while (str.length() > 0 && str.charAt(0) == ch) {
            str = str.substring(1);
        }
        return str;
    }

    private String makeAbsoluteImageUrl(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) return "";

        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }

        String cleanPath = ltrim(imagePath, '/');
        return ApiRoutes.BASE_URL + "/" + cleanPath;
    }

    // ================= Header =================

    private void bindHeader() {
        btnDrawer = findViewById(R.id.btnDrawer);
        btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        etSearch = findViewById(R.id.etSearch);
        tvLocation = findViewById(R.id.tvlocation);

        ImageView headerOverlay = findViewById(R.id.headerOverlay);
        if (headerOverlay != null) {
            headerOverlay.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        }
    }

    // ================= Header Location =================

    private void fetchAndDisplayHeaderLocation() {
        if (tvLocation == null || fusedLocationClient == null) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    HEADER_LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        updateHeaderLocationText(location.getLatitude(), location.getLongitude());
                    } else {
                        requestFreshHeaderLocation();
                    }
                })
                .addOnFailureListener(this, e ->
                        Log.e(TAG, "Header location fetch failed", e));
    }

    @SuppressLint("MissingPermission")
    private void requestFreshHeaderLocation() {
        com.google.android.gms.location.LocationRequest req =
                com.google.android.gms.location.LocationRequest.create()
                        .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setNumUpdates(1)
                        .setInterval(3000)
                        .setMaxWaitTime(8000);

        fusedLocationClient.requestLocationUpdates(
                req,
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(com.google.android.gms.location.LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);

                        if (result != null && result.getLastLocation() != null) {
                            updateHeaderLocationText(
                                    result.getLastLocation().getLatitude(),
                                    result.getLastLocation().getLongitude()
                            );
                        }
                    }
                },
                android.os.Looper.getMainLooper()
        );
    }

    private void updateHeaderLocationText(double lat, double lng) {
        new Thread(() -> {
            String finalLocation = "";

            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    String district = firstNonEmpty(
                            address.getSubAdminArea(),
                            address.getLocality(),
                            address.getSubLocality()
                    );

                    String state = firstNonEmpty(address.getAdminArea());

                    if (!TextUtils.isEmpty(district) && !TextUtils.isEmpty(state)) {
                        finalLocation = district + ", " + state;
                    } else if (!TextUtils.isEmpty(state)) {
                        finalLocation = state;
                    } else if (!TextUtils.isEmpty(district)) {
                        finalLocation = district;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Geocoder failed", e);
            }

            final String textToShow = finalLocation;
            runOnUiThread(() -> {
                if (tvLocation != null && !TextUtils.isEmpty(textToShow)) {
                    tvLocation.setText(textToShow);
                }
            });
        }).start();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value.trim();
            }
        }
        return "";
    }

    // ================= Voice =================

    private void setupVoiceLauncher() {
        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> list =
                                result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (list != null && !list.isEmpty()) {
                            etSearch.setText(list.get(0));
                            performSearch(list.get(0));
                        }
                    }
                });

        if (btnVoiceSearch != null) {
            btnVoiceSearch.setOnClickListener(v -> startVoiceInput());
        }
    }

    private void startVoiceInput() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, I18n.t(this, "Speak to search…"));
        try {
            speechLauncher.launch(i);
        } catch (Exception e) {
            makeText(this, I18n.t(this, "Voice search not available"), Toast.LENGTH_SHORT).show();
        }
    }

    // ================= Search =================

    private void setupSearch() {
        if (etSearch == null) return;

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
                performSearch(q);

                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (ignoreSearchTextChanges) return;

                String q = s == null ? "" : s.toString().trim();
                performSearch(q);
            }
        });
    }

    private void cancelPendingSearch() {
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    private void clearSearchSilently() {
        cancelPendingSearch();
        searchQuery = "";

        if (etSearch != null) {
            ignoreSearchTextChanges = true;
            etSearch.setText("");
            ignoreSearchTextChanges = false;
        }
    }

    private void clearSearch() {
        clearSearchSilently();
    }

    private void performSearch(String query) {
        cancelPendingSearch();

        searchRunnable = () -> {
            ensureProductsView();
            searchQuery = TextUtils.isEmpty(query) ? "" : query.toLowerCase(Locale.ROOT).trim();
            resetPagination();
            fetchProducts();
        };

        searchHandler.postDelayed(searchRunnable, 500);
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
        LanguageManager.apply(this, lang);
    }

    // ================= Language / Drawer =================

    private void setupLanguageToggle() {
        if (btnDrawer == null) return;

        btnDrawer.setOnClickListener(v -> {
            if (drawerLayout != null) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
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
    private void applyDrawerWidth60Percent() {
        if (navigationView == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int drawerWidth = (int) (screenWidth * 0.60f);

        DrawerLayout.LayoutParams params =
                (DrawerLayout.LayoutParams) navigationView.getLayoutParams();
        params.width = drawerWidth;
        navigationView.setLayoutParams(params);
    }
    private void setupAppDrawer() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navView);
        if (drawerLayout == null || navigationView == null) return;
        applyDrawerWidth60Percent();
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                android.R.string.ok, android.R.string.cancel);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        View header = navigationView.getHeaderView(0);
        if (header != null) {
            ImageView ivProfile = header.findViewById(R.id.ivProfile);
            tvNavUserName = header.findViewById(R.id.tvUserName);
            tvNavUserPhone = header.findViewById(R.id.tvUserPhone);
            ImageView ivEdit = header.findViewById(R.id.ivEdit);

            if (tvNavUserName != null) {
                tvNavUserName.setText(I18n.t(this, cachedUserName));
            }
            if (tvNavUserPhone != null) {
                tvNavUserPhone.setText(cachedUserPhone);
            }

            View.OnClickListener openProfileClick = v ->
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class));

            header.setOnClickListener(openProfileClick);
            if (ivProfile != null) ivProfile.setOnClickListener(openProfileClick);
            if (ivEdit != null) ivEdit.setOnClickListener(openProfileClick);
        }

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
                startActivity(new Intent(MainActivity.this, MyAdsActivity.class));

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

    private void applyDrawerMenuTranslations() {
        if (navigationView == null) return;
        android.view.Menu menu = navigationView.getMenu();
        if (menu == null) return;

        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            if (item != null && item.getTitle() != null) {
                item.setTitle(I18n.t(this, item.getTitle().toString()));
            }
        }
    }

    private void doLogout() {
        getSharedPreferences("user", MODE_PRIVATE).edit().clear().apply();
        makeText(this, I18n.t(this, "Logged out"), Toast.LENGTH_SHORT).show();

        Intent i = new Intent(this, LanguageSelection.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ================= Static text prefetch =================

    private void prefetchAndApplyStaticTexts() {
        List<String> keys = new ArrayList<>();

        if (etSearch != null && etSearch.getHint() != null) keys.add(etSearch.getHint().toString());
        if (tvSectionTitle != null && tvSectionTitle.getText() != null) keys.add(tvSectionTitle.getText().toString());
        if (tvMarquee != null && tvMarquee.getText() != null) keys.add(tvMarquee.getText().toString());

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
        keys.add("Condition: All");
        keys.add("Condition: New");
        keys.add("Condition: Used");
        keys.add("New Items Only");
        keys.add("Used Items Only");
        keys.add("Logged out");
        keys.add("Nearby");
        keys.add("Getting your location...");
        keys.add("Location error. Please try again.");
        keys.add("Could not get location. Try again.");
        keys.add("Enable Location");
        keys.add("GPS is turned off. Please enable location services to filter listings by distance.");
        keys.add("Open Settings");
        keys.add("Cancel");
        keys.add("Location permission needed for distance filter");
        keys.add("GPS is required for distance filter");

        if (navigationView != null && navigationView.getMenu() != null) {
            android.view.Menu menu = navigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                android.view.MenuItem item = menu.getItem(i);
                if (item != null && item.getTitle() != null) {
                    keys.add(item.getTitle().toString());
                }
            }
        }

        I18n.prefetch(this, keys, () -> {
            if (etSearch != null && etSearch.getHint() != null) {
                etSearch.setHint(I18n.t(this, etSearch.getHint().toString()));
            }
            if (tvSectionTitle != null && tvSectionTitle.getText() != null) {
                tvSectionTitle.setText(I18n.t(this, tvSectionTitle.getText().toString()));
            }
            if (tvMarquee != null && tvMarquee.getText() != null) {
                tvMarquee.setText(I18n.t(this, tvMarquee.getText().toString()));
            }
            applyDrawerMenuTranslations();
            if (chipCondition != null && chipCondition.getText() != null) {
                chipCondition.setText(I18n.t(this, chipCondition.getText().toString()));
            }
        });
    }

    // ================= Adapters =================

    private void setupAdapters() {
        catAdapter = new CategoryAdapter(categories, cat -> {
            selectedCategoryId = toInt(cat.get("id"), -1);
            selectedSubFilterId = -1;
            showNew = null;

            clearSearchSilently();

            if (chipCondition != null) {
                chipCondition.setVisibility(View.GONE);
                chipCondition.setText(I18n.t(this, "Condition: All"));
                showNew = null;
            }

            resetPagination();

            // Important: stop any old product requests so they do not overwrite the subcategory screen
            VolleySingleton.getInstance(this).getQueue().cancelAll(TAG_FETCH_PRODUCTS);
            productsInFlight = false;
            lastProductsUrl = null;

            if (selectedCategoryId == 0) {
                mapSubFilters.remove(0);
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

    private void bindSubFilters(List<Map<String, Object>> subs) {
        rvSubFiltersGrid.setAdapter(new SubFilterGridAdapter(subs, sub -> {
            selectedSubFilterId = toInt(sub.get("id"), 0);

            clearSearchSilently();
            showProducts();

            if (selectedSubFilterId > 0) {
                boolean hasNewOld = toBool(sub.get("hasNewOld"), false);
                if (hasNewOld && chipCondition != null) {
                    chipCondition.setVisibility(View.VISIBLE);
                } else if (chipCondition != null) {
                    chipCondition.setVisibility(View.GONE);
                    showNew = null;
                    chipCondition.setText(I18n.t(this, "Condition: All"));
                }
            } else {
                if (chipCondition != null) {
                    chipCondition.setVisibility(View.GONE);
                    showNew = null;
                    chipCondition.setText(I18n.t(this, "Condition: All"));
                }
            }

            productCache.evictAll();
            resetPagination();
            fetchProducts();
        }));

        showSubFilters();
        if (catAdapter != null) {
            catAdapter.setSelectedId(selectedCategoryId);
        }
    }

    private void bindProducts(List<Map<String, Object>> items) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

        if (items == null || items.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (productAdapterRef != null) productAdapterRef.setItems(items);
            runLayoutAnimation();
        }
    }

    private void runLayoutAnimation() {
        if (rvProducts == null) return;
        final LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                this, R.anim.layout_animation_fall_down);
        rvProducts.setLayoutAnimation(controller);
        rvProducts.scheduleLayoutAnimation();
    }

    private void showSubFilters() {
        rvProducts.setVisibility(View.GONE);
        if (tvSectionTitle != null) tvSectionTitle.setVisibility(View.GONE);
        rvSubFiltersGrid.setVisibility(View.VISIBLE);
    }

    private void showProducts() {
        rvSubFiltersGrid.setVisibility(View.GONE);
        if (tvSectionTitle != null) {
            tvSectionTitle.setVisibility(View.VISIBLE);
            tvSectionTitle.setText(I18n.t(this, "Featured Listings"));
        }
        rvProducts.setVisibility(View.VISIBLE);
    }

    private void ensureProductsView() {
        if (rvSubFiltersGrid != null && rvSubFiltersGrid.getVisibility() == View.VISIBLE) {
            showProducts();
        }
    }

    // ================= URLs =================

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

        if (selectedCategoryId > 0) sb.append("&category_id=").append(selectedCategoryId);
        if (selectedSubFilterId > 0) sb.append("&subcategory_id=").append(selectedSubFilterId);
        if (!TextUtils.isEmpty(searchQuery)) {
            sb.append("&q=").append(android.net.Uri.encode(searchQuery));
        }
        if (showNew != null) {
            sb.append("&is_new=").append(showNew ? "1" : "0");
        }

        if (selectedRadiusKm != null && userLat != null && userLng != null) {
            sb.append("&lat=").append(userLat)
                    .append("&lng=").append(userLng)
                    .append("&radius=").append(selectedRadiusKm);
        }

        return sb.toString();
    }

    private void resetPagination() {
        currentPage = 1;
        hasMoreProducts = true;
        isLoadingMore = false;
        productsInFlight = false;
        lastProductsUrl = null;
    }

    private void loadMoreProducts() {
        if (isLoadingMore || !hasMoreProducts) return;
        isLoadingMore = true;
        currentPage++;
        fetchProducts();
    }

    // ================= Network =================

    private void fetchCategories() {
        final String url = urlCategories();
        Log.e(TAG, "========== FETCH CATEGORIES START ==========");
        Log.e(TAG, "URL: " + url);

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

                        Map<String, Object> allCat = new HashMap<>();
                        allCat.put("id", 0);
                        allCat.put("name", "All Listings");
                        allCat.put("iconRes", R.drawable.ic_all_listings);
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

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl)) {
                                    iconUrl = o.optString("icon_url", "");
                                }

                                iconUrl = makeAbsoluteImageUrl(iconUrl);
                                m.put("iconUrl", iconUrl);
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                categories.add(m);

                                if (!TextUtils.isEmpty(nameEn)) {
                                    catNameKeys.add(nameEn);
                                }
                            }
                        } else {
                            Log.e(TAG, "fetchCategories(): data array is NULL!");
                        }

                        if (catAdapter != null) catAdapter.notifyDataSetChanged();

                        I18n.prefetch(this, catNameKeys, () -> {
                            for (Map<String, Object> m : categories) {
                                Object nObj = m.get("name");
                                if (nObj != null) {
                                    String en = String.valueOf(nObj);
                                    m.put("name", I18n.t(this, en));
                                }
                            }
                            if (catAdapter != null) catAdapter.notifyDataSetChanged();
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "fetchCategories(): parse exception", e);
                        makeText(this, I18n.t(this, "Parse categories failed"), Toast.LENGTH_SHORT).show();
                    }
                },
                err -> {
                    Log.e(TAG, "❌ Categories Fetch Error: " + err);
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

        req.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        req.setShouldCache(false);
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchSubFilters(int categoryId) {
        final String url = urlSubcategories(categoryId);

        if (mapSubFilters.containsKey(categoryId)) {
            List<Map<String, Object>> subs = mapSubFilters.get(categoryId);
            bindSubFilters(subs);
            return;
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
                        all.put("name", I18n.t(this, "All"));
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

                                String iconUrl = o.optString("icon", "");
                                if (TextUtils.isEmpty(iconUrl)) {
                                    iconUrl = o.optString("icon_url", "");
                                }

                                iconUrl = makeAbsoluteImageUrl(iconUrl);
                                m.put("iconUrl", iconUrl);
                                m.put("hasNewOld", o.optInt("hasNewOld", 0) == 1);

                                subs.add(m);
                            }
                        } else {
                            Log.e(TAG, "fetchSubFilters(): data array is NULL!");
                        }

                        List<String> subNameKeys = new ArrayList<>();
                        for (Map<String, Object> m : subs) {
                            Object nObj = m.get("name");
                            if (nObj != null) {
                                subNameKeys.add(String.valueOf(nObj));
                            }
                        }

                        I18n.prefetch(this, subNameKeys, () -> {
                            for (Map<String, Object> m : subs) {
                                Object nObj = m.get("name");
                                if (nObj != null) {
                                    String en = String.valueOf(nObj);
                                    m.put("name", I18n.t(this, en));
                                }
                            }
                            mapSubFilters.put(categoryId, subs);
                            bindSubFilters(subs);
                        }, () -> {
                            mapSubFilters.put(categoryId, subs);
                            bindSubFilters(subs);
                        });

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
        final boolean isAppend = currentPage > 1;

        if (!isAppend && productsInFlight) {
            VolleySingleton.getInstance(this).getQueue().cancelAll(TAG_FETCH_PRODUCTS);
        }
        productsInFlight = true;

        if (!isAppend) {
            JSONObject cachedResp = productCache.get(url);
            if (cachedResp != null) {
                try {
                    productsInFlight = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    parseProductsResponse(cachedResp, false);
                    lastProductsUrl = url;
                    return;
                } catch (Exception ignored) {
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

                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                    if (resp != null) {
                        if (!isAppend) productCache.put(url, resp);
                        parseProductsResponse(resp, isAppend);
                    }
                },
                err -> {
                    Log.e(TAG, "fetchProducts() ERROR: " + err);
                    productsInFlight = false;
                    isLoadingMore = false;

                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

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

    private void parseProductsResponse(JSONObject resp, boolean isAppend) {
        try {
            if (!"success".equalsIgnoreCase(resp.optString("status"))) {
                Log.e(TAG, "parseProductsResponse(): status != success");
                makeText(this, I18n.t(this, "Products error"), Toast.LENGTH_SHORT).show();
                if (!isAppend) bindProducts(new ArrayList<>());
                return;
            }

            hasMoreProducts = resp.optBoolean("has_more", false);

            JSONArray arr = resp.optJSONArray("data");
            List<Map<String, Object>> newItems = new ArrayList<>();

            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", o.optInt("id", 0));
                    m.put("category_id", o.optInt("category_id", 0));
                    m.put("subFilterId", o.optInt("subcategory_id", 0));
                    m.put("title", o.optString("title", ""));
                    m.put("price", String.valueOf(o.opt("price")));
                    m.put("city", o.optString("city", ""));

                    if (!o.isNull("distance")) {
                        m.put("distance", String.valueOf(o.opt("distance")));
                    }

                    String imageUrl = "";
                    if (!o.isNull("cover_image")) imageUrl = o.optString("cover_image", "");
                    if (TextUtils.isEmpty(imageUrl) && !o.isNull("image_url")) imageUrl = o.optString("image_url", "");
                    if (TextUtils.isEmpty(imageUrl) && !o.isNull("image")) imageUrl = o.optString("image", "");

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
                    if (images.isEmpty() && !TextUtils.isEmpty(String.valueOf(m.get("imageUrl")))) {
                        images.add(String.valueOf(m.get("imageUrl")));
                    }
                    m.put("images", images);

                    newItems.add(m);
                }
            }

            if (isAppend) {
                currentProducts.addAll(newItems);
                if (productAdapterRef != null) {
                    productAdapterRef.addItems(newItems);
                }
            } else {
                currentProducts.clear();
                currentProducts.addAll(newItems);
                bindProducts(new ArrayList<>(currentProducts));
            }

            LoadingDialog.hideLoading();

        } catch (Exception e) {
            Log.e(TAG, "parseProductsResponse(): exception", e);
            if (!isAppend) bindProducts(new ArrayList<>());
        }
    }

    // ================= KM Filter =================

    private void showKmFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_km_filter, null);
        dialog.setContentView(sheet);

        ChipGroup chipGroup = sheet.findViewById(R.id.chipGroupKm);

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
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);

            if (checkedId == R.id.chipAll) {
                selectedRadiusKm = null;
                updateKmChipText();
                dialog.dismiss();
                applyKmFilter();
            } else {
                int km = 10;
                if (checkedId == R.id.chip5km) km = 5;
                else if (checkedId == R.id.chip10km) km = 10;
                else if (checkedId == R.id.chip25km) km = 25;
                else if (checkedId == R.id.chip50km) km = 50;
                else if (checkedId == R.id.chip100km) km = 100;

                selectedRadiusKm = km;
                updateKmChipText();
                dialog.dismiss();

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
        if (chipKmFilter == null) return;
        if (selectedRadiusKm == null) {
            chipKmFilter.setText(I18n.t(this, "Nearby"));
        } else {
            chipKmFilter.setText(selectedRadiusKm + " km");
        }
    }

    private void applyKmFilter() {
        ensureProductsView();
        productCache.evictAll();
        lastProductsUrl = null;
        productsInFlight = false;
        resetPagination();
        fetchProducts();
    }

    @SuppressLint("MissingPermission")
    private void fetchUserLocationThenFilter() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        com.google.android.gms.location.LocationRequest locationRequest =
                com.google.android.gms.location.LocationRequest.create()
                        .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);

        com.google.android.gms.location.LocationSettingsRequest settingsRequest =
                new com.google.android.gms.location.LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true)
                        .build();

        LocationServices.getSettingsClient(this)
                .checkLocationSettings(settingsRequest)
                .addOnSuccessListener(this, response -> doFetchLocation())
                .addOnFailureListener(this, e -> {
                    if (e instanceof com.google.android.gms.common.api.ResolvableApiException) {
                        try {
                            com.google.android.gms.common.api.ResolvableApiException resolvable =
                                    (com.google.android.gms.common.api.ResolvableApiException) e;
                            locationSettingsLauncher.launch(
                                    new androidx.activity.result.IntentSenderRequest.Builder(
                                            resolvable.getResolution()).build());
                        } catch (Exception ex) {
                            Log.e(TAG, "Could not show location settings dialog", ex);
                            showManualGpsPrompt();
                        }
                    } else {
                        showManualGpsPrompt();
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void doFetchLocation() {
        LoadingDialog.showLoading(this, I18n.t(this, "Getting your location..."));
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    LoadingDialog.hideLoading();
                    if (location != null) {
                        userLat = location.getLatitude();
                        userLng = location.getLongitude();
                        Log.d(TAG, "User location: " + userLat + ", " + userLng);
                        applyKmFilter();
                    } else {
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(this, e -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "Location fetch failed", e);
                    makeText(this, I18n.t(this, "Location error. Please try again."), Toast.LENGTH_SHORT).show();
                    selectedRadiusKm = null;
                    updateKmChipText();
                });
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        com.google.android.gms.location.LocationRequest req =
                com.google.android.gms.location.LocationRequest.create()
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
                            makeText(MainActivity.this,
                                    I18n.t(MainActivity.this, "Could not get location. Try again."),
                                    Toast.LENGTH_SHORT).show();
                            selectedRadiusKm = null;
                            updateKmChipText();
                        }
                    }
                }, android.os.Looper.getMainLooper());
    }

    private void showManualGpsPrompt() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(I18n.t(this, "Enable Location"))
                .setMessage(I18n.t(this, "GPS is turned off. Please enable location services to filter listings by distance."))
                .setPositiveButton(I18n.t(this, "Open Settings"), (d, w) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton(I18n.t(this, "Cancel"), (d, w) -> {
                    selectedRadiusKm = null;
                    updateKmChipText();
                })
                .setCancelable(false)
                .show();
    }

    // ================= Permission =================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == HEADER_LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchAndDisplayHeaderLocation();
            }
            return;
        }

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchUserLocationThenFilter();
            } else {
                makeText(this, I18n.t(this, "Location permission needed for distance filter"), Toast.LENGTH_SHORT).show();
                selectedRadiusKm = null;
                updateKmChipText();
            }
        }
    }

    // ================= User Profile =================

    @SuppressLint("SetTextI18n")
    private void fetchUserProfile(TextView tvUserName, TextView tvUserPhone) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            if (tvUserName != null) tvUserName.setText(I18n.t(this, "Guest User"));
            if (tvUserPhone != null) tvUserPhone.setText("");
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
                                tvUserName.setText(I18n.t(this, name));
                            }
                            if (tvUserPhone != null) {
                                tvUserPhone.setText(formattedPhone);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing user profile response", e);
                    }
                },
                error -> {
                    Log.e(TAG, "❌ USER PROFILE API ERROR", error);

                    if (tvUserName != null) {
                        tvUserName.setText(I18n.t(this, "User"));
                    }
                    if (tvUserPhone != null) {
                        tvUserPhone.setText("");
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(
                5000,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchUserProfileOnStartup() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            cachedUserName = I18n.t(this, "Guest User");
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
                            cachedUserName = I18n.t(this, user.optString("name", "User"));
                            cachedUserPhone = user.optString("formatted_phone", "");
                            session.saveUserProfile(cachedUserName, cachedUserPhone);

                            runOnUiThread(() -> {
                                if (tvNavUserName != null) {
                                    tvNavUserName.setText(I18n.t(this, cachedUserName));
                                }
                                if (tvNavUserPhone != null) {
                                    tvNavUserPhone.setText(cachedUserPhone);
                                }
                            });
                        } else {
                            cachedUserName = I18n.t(this, "User");
                            cachedUserPhone = "";
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error parsing user profile response", e);
                        cachedUserName = I18n.t(this, "User");
                        cachedUserPhone = "";
                    }
                },
                error -> {
                    Log.e(TAG, "❌ USER PROFILE API ERROR ON STARTUP", error);
                    cachedUserName = "User";
                    cachedUserPhone = "";
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                1,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    // ================= Condition popup =================

    private void showConditionPopup() {
        if (chipCondition == null) return;

        androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(this, chipCondition);
        popup.getMenu().add(0, 0, 0, I18n.t(this, "Condition: All"));
        popup.getMenu().add(0, 1, 1, I18n.t(this, "New Items Only"));
        popup.getMenu().add(0, 2, 2, I18n.t(this, "Used Items Only"));

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 0) {
                showNew = null;
                chipCondition.setText(I18n.t(this, "Condition: All"));
            } else if (id == 1) {
                showNew = true;
                chipCondition.setText(I18n.t(this, "Condition: New"));
            } else if (id == 2) {
                showNew = false;
                chipCondition.setText(I18n.t(this, "Condition: Used"));
            }

            productCache.evictAll();
            resetPagination();
            fetchProducts();
            return true;
        });

        popup.show();
    }

    // ================= Back =================

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }

        if (rvSubFiltersGrid != null && rvSubFiltersGrid.getVisibility() == View.VISIBLE) {
            showProducts();
            selectedSubFilterId = -1;
            productsInFlight = false;
            lastProductsUrl = null;
            resetPagination();
            fetchProducts();
            return;
        }

        if (selectedCategoryId != -1) {
            selectedCategoryId = -1;
            selectedSubFilterId = -1;
            showNew = null;

            if (chipCondition != null) {
                chipCondition.setVisibility(View.GONE);
            }
            if (catAdapter != null) {
                catAdapter.setSelectedId(-1);
            }

            showProducts();
            clearSearchSilently();

            productsInFlight = false;
            lastProductsUrl = null;
            resetPagination();
            fetchProducts();
            return;
        }

        super.onBackPressed();
    }

    // ================= Misc =================

    private static int toInt(Object o, int def) {
        if (o instanceof Integer) return (Integer) o;
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean toBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o == null) return def;

        String s = String.valueOf(o);
        if ("1".equals(s)) return true;
        if ("0".equals(s)) return false;

        try {
            return Boolean.parseBoolean(s);
        } catch (Exception e) {
            return def;
        }
    }

    private String safeJsonSnippet(JSONObject obj) {
        try {
            String s = obj == null ? "null" : obj.toString();
            if (s.length() > 500) return s.substring(0, 500) + "...";
            return s;
        } catch (Exception e) {
            return "json_snippet_error";
        }
    }

    private String buildVolleyError(VolleyError err) {
        if (err == null) return "VolleyError=null";

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
                    String body = new String(err.networkResponse.data).trim();
                    if (body.length() > 300) body = body.substring(0, 300) + "...";
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
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }
}