package com.anvexgroup.sheharsetu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.anvexgroup.sheharsetu.Adapter.CategoryGridAdapter;
import com.anvexgroup.sheharsetu.Adapter.I18n;
import com.anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.anvexgroup.sheharsetu.Adapter.SubcategoryGridAdapter;
import com.anvexgroup.sheharsetu.net.ApiRoutes;
import com.anvexgroup.sheharsetu.net.VolleySingleton;
import com.anvexgroup.sheharsetu.utils.CategoryCache;
import com.anvexgroup.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategorySelectActivity extends AppCompatActivity {

    private static final String TAG = "CategorySelectActivity";

    private MaterialToolbar topBar;
    private RecyclerView rvCategories, rvSubcategories;
    private TextView tvSubTitle;
    private View conditionRow;
    private ChipGroup cgCondition;
    private Chip chipNew, chipUsed;
    private MaterialButton btnContinue;

    private final List<Category> categories = new ArrayList<>();
    private final Map<String, List<Subcategory>> subMap = new HashMap<>();

    private Category selectedCategory = null;
    private Subcategory selectedSub = null;
    private String selectedCondition = null;

    private CategoryGridAdapter categoryAdapter;
    private SubcategoryGridAdapter subcategoryAdapter;

    private static final String PREFS = LanguageSelection.PREFS;
    private static final String KEY_LANG = LanguageSelection.KEY_LANG_CODE;

    private CategoryCache categoryCache;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applySavedLocale();

        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_category_select);
bindViews();
        prefetchAndApplyStaticTexts();
        setupLists();
        setupClicks();

        categoryCache = new CategoryCache(this);


        loadCategoriesWithSmartCache();
        updateCtaState();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
LanguageManager.apply(this, lang);
    }

    private void bindViews() {
        topBar = findViewById(R.id.topBar);
        if (topBar != null) {
            topBar.setNavigationOnClickListener(v -> onBackPressed());
        }

        rvCategories = findViewById(R.id.rvCategories);
        rvSubcategories = findViewById(R.id.rvSubcategories);
        tvSubTitle = findViewById(R.id.tvSubTitle);

        conditionRow = findViewById(R.id.conditionRow);
        cgCondition = findViewById(R.id.cgCondition);
        chipNew = findViewById(R.id.chipNew);
        chipUsed = findViewById(R.id.chipUsed);

        btnContinue = findViewById(R.id.btnContinue);
}

    private void prefetchAndApplyStaticTexts() {
        List<String> keys = new ArrayList<>();

        if (topBar != null && topBar.getTitle() != null)
            keys.add(topBar.getTitle().toString());
        if (tvSubTitle != null && tvSubTitle.getText() != null)
            keys.add(tvSubTitle.getText().toString());
        if (chipNew != null && chipNew.getText() != null)
            keys.add(chipNew.getText().toString());
        if (chipUsed != null && chipUsed.getText() != null)
            keys.add(chipUsed.getText().toString());
        if (btnContinue != null && btnContinue.getText() != null)
            keys.add(btnContinue.getText().toString());

        keys.add("Failed to load categories.");
        keys.add("Parsing error (categories).");
        keys.add("Unable to load categories. Please check internet.");
        keys.add("Failed to load subcategories.");
        keys.add("Parsing error (subcategories).");
        keys.add("Unable to load subcategories. Please check internet.");
        keys.add("Please select a category.");
        keys.add("Please select a subcategory.");
        keys.add("Please select condition (New/Used).");

        I18n.prefetch(this, keys, () -> {
            if (topBar != null && topBar.getTitle() != null) {
                topBar.setTitle(I18n.t(this, topBar.getTitle().toString()));
            }
            if (tvSubTitle != null && tvSubTitle.getText() != null) {
                tvSubTitle.setText(I18n.t(this, tvSubTitle.getText().toString()));
            }
            if (chipNew != null && chipNew.getText() != null) {
                chipNew.setText(I18n.t(this, chipNew.getText().toString()));
            }
            if (chipUsed != null && chipUsed.getText() != null) {
                chipUsed.setText(I18n.t(this, chipUsed.getText().toString()));
            }
            if (btnContinue != null && btnContinue.getText() != null) {
                btnContinue.setText(I18n.t(this, btnContinue.getText().toString()));
            }
        });
    }

    private void setupLists() {
        rvCategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvCategories.setHasFixedSize(true);
        addGridSpacing(rvCategories, 12);

        categoryAdapter = new CategoryGridAdapter(mapToCategoryItems(categories), (item, pos) -> {
selectedCategory = new Category(item.id, item.name, item.iconUrl, item.requiresCondition);
            selectedSub = null;
            selectedCondition = null;

            loadSubcategories(item.id);

            if (tvSubTitle != null)
                tvSubTitle.setVisibility(View.VISIBLE);
            if (rvSubcategories != null)
                rvSubcategories.setVisibility(View.VISIBLE);

            if (conditionRow != null)
                conditionRow.setVisibility(View.GONE);
            if (cgCondition != null)
                cgCondition.clearCheck();

            updateCtaState();
        });
        rvCategories.setAdapter(categoryAdapter);

        rvSubcategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvSubcategories.setHasFixedSize(true);
        addGridSpacing(rvSubcategories, 12);

        subcategoryAdapter = new SubcategoryGridAdapter(new ArrayList<>(), (s, pos) -> {
selectedSub = new Subcategory(s.id, s.parentId, s.name, s.iconUrl, s.requiresCondition);

            boolean needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : (selectedCategory != null && selectedCategory.requiresCondition);

            if (conditionRow != null)
                conditionRow.setVisibility(needCond ? View.VISIBLE : View.GONE);
            if (!needCond)
                selectedCondition = null;
            if (cgCondition != null)
                cgCondition.clearCheck();

            updateCtaState();
        });
        rvSubcategories.setAdapter(subcategoryAdapter);
    }

    private void setupClicks() {
        if (cgCondition != null) {
            cgCondition.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.chipNew)
                    selectedCondition = "new";
                else if (checkedId == R.id.chipUsed)
                    selectedCondition = "used";
                else
                    selectedCondition = null;
updateCtaState();
            });
        }

        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> {
if (selectedCategory == null) {
                    toast(I18n.t(this, "Please select a category."));
                    return;
                }
                if (selectedSub == null) {
                    toast(I18n.t(this, "Please select a subcategory."));
                    return;
                }

                boolean needCond = (selectedSub.requiresCondition != null)
                        ? selectedSub.requiresCondition
                        : selectedCategory.requiresCondition;

                if (needCond && selectedCondition == null) {
                    toast(I18n.t(this, "Please select condition (New/Used)."));
                    return;
                }

                Intent intent = new Intent(CategorySelectActivity.this, DynamicFormActivity.class);
                intent.putExtra(DynamicFormActivity.EXTRA_CATEGORY, selectedCategory.name);

                intent.putExtra("category_id", selectedCategory.id);
                intent.putExtra("category_name", selectedCategory.name);
                intent.putExtra("subcategory_id", selectedSub.id);
                intent.putExtra("subcategory_name", selectedSub.name);
                if (selectedCondition != null)
                    intent.putExtra("condition", selectedCondition);
startActivity(intent);
            });
        }
    }

    private void loadCategoriesWithSmartCache() {
        List<CategoryCache.CachedCategory> cachedCategories = categoryCache.loadCategories();

        if (!cachedCategories.isEmpty()) {
            categories.clear();
            List<String> catNameKeys = new ArrayList<>();
            for (CategoryCache.CachedCategory cached : cachedCategories) {
                categories.add(new Category(cached.id, cached.name, cached.iconUrl, cached.requiresCondition));
                catNameKeys.add(cached.name);
            }

            I18n.prefetch(this, catNameKeys, () -> {
                categoryAdapter.submit(mapToCategoryItems(categories));
});

            refreshCategoriesFromApi(false);
        } else {
            refreshCategoriesFromApi(true);
        }
    }

    /**
     * Fetch categories from API and update cache
     * 
     * @param showLoader Whether to show loading dialog
     */
    private void refreshCategoriesFromApi(boolean showLoader) {
        final String url = ApiRoutes.GET_CATEGORIES;
        if (showLoader) {
            LoadingDialog.showLoading(this, "Loading categories...");
        }

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONObject root = new JSONObject(response);

                        boolean ok = "success".equalsIgnoreCase(root.optString("status"))
                                || root.optBoolean("ok", false)
                                || root.optBoolean("success", false)
                                || root.optInt("success", 0) == 1;

                        if (!ok) {
                            String msg = root.optString("message", "Failed to load categories.");
                            Log.e(TAG, "Categories ok=false message=" + msg);
                            if (showLoader) {
                                LoadingDialog.hideLoading();
                                toast(I18n.t(this, msg));
                            }
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        if (dataArr == null)
                            dataArr = root.optJSONArray("categories");

                        List<Category> freshCategories = new ArrayList<>();
                        List<CategoryCache.CachedCategory> toCache = new ArrayList<>();
                        List<String> catNameKeys = new ArrayList<>();

                        if (dataArr != null) {
for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.optJSONObject(i);
                                if (obj == null)
                                    continue;

                                String id = firstNonEmpty(
                                        obj.optString("id", ""),
                                        obj.optString("category_id", ""),
                                        String.valueOf(obj.optInt("id", 0)),
                                        String.valueOf(obj.optInt("category_id", 0))).trim();

                                String name = firstNonEmpty(
                                        obj.optString("name", ""),
                                        obj.optString("category_name", "")).trim();

                                String iconUrl = firstNonEmpty(
                                        obj.optString("icon", ""),
                                        obj.optString("icon_url", "")).trim();

                                boolean requiresCond = obj.optInt("hasNewOld", obj.optInt("has_new_old", 0)) == 1;

                                if (!TextUtils.isEmpty(id) && !"0".equals(id) && !TextUtils.isEmpty(name)) {
                                    freshCategories.add(new Category(id, name, iconUrl, requiresCond));
                                    toCache.add(new CategoryCache.CachedCategory(id, name, iconUrl, requiresCond));
                                    catNameKeys.add(name);
}
                            }
                        }

                        // Save fresh data to cache
                        categoryCache.saveCategories(toCache);
// Update UI with fresh data
                        categories.clear();
                        categories.addAll(freshCategories);
I18n.prefetch(this, catNameKeys, () -> {
                            if (showLoader)
                                LoadingDialog.hideLoading();
                            categoryAdapter.submit(mapToCategoryItems(categories));
});

                    } catch (JSONException e) {
                        Log.e(TAG, "Categories parse error", e);
                        if (showLoader) {
                            LoadingDialog.hideLoading();
                            toast(I18n.t(this, "Parsing error (categories)."));
                        }
                    }
                },
                error -> {
                    if (showLoader) {
                        LoadingDialog.hideLoading();
                        toast(I18n.t(this, "Unable to load categories. Please check internet."));
                    }
                    Log.e(TAG, "Categories request failed: " + describeVolleyError(error));
                }) {

            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Accept-Language", I18n.lang(CategorySelectActivity.this));
                return h;
            }

        };

        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }

    /**
     * @deprecated Use loadCategoriesWithSmartCache() instead
     */
    @Deprecated
    private void loadCategoriesFromApi() {
        refreshCategoriesFromApi(true);
    }

    private void loadSubcategories(String catId) {
        if (subMap.containsKey(catId)) {
            List<Subcategory> cached = subMap.get(catId);
displaySubcategories(cached);
            refreshSubcategoriesFromApi(catId, false);
            return;
        }

        List<CategoryCache.CachedSubcategory> persistentCached = categoryCache.loadSubcategories(catId);
        if (persistentCached != null && !persistentCached.isEmpty()) {
// Convert to Subcategory model
            List<Subcategory> subs = new ArrayList<>();
            for (CategoryCache.CachedSubcategory cs : persistentCached) {
                subs.add(new Subcategory(cs.id, cs.parentId, cs.name, cs.iconUrl, cs.requiresCondition));
            }

            subMap.put(catId, subs);

            displaySubcategories(subs);

            refreshSubcategoriesFromApi(catId, false);
            return;
        }

refreshSubcategoriesFromApi(catId, true);
    }

    private void displaySubcategories(List<Subcategory> subs) {
        if (subs == null || subs.isEmpty()) {
            subcategoryAdapter.submit(new ArrayList<>());
            return;
        }

        List<String> subNameKeys = new ArrayList<>();
        for (Subcategory s : subs) {
            subNameKeys.add(s.name);
        }

        I18n.prefetch(this, subNameKeys, () -> {
            List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
            for (Subcategory s : subs) {
                ui.add(new SubcategoryGridAdapter.Item(
                        s.id,
                        s.parentId,
                        I18n.t(this, s.name),
                        s.iconUrl,
                        s.requiresCondition));
            }
            subcategoryAdapter.submit(ui);
});
    }

    /**
     * Fetch subcategories from API and update caches
     * 
     * @param catId      Category ID
     * @param showLoader Whether to show loading dialog
     */
    private void refreshSubcategoriesFromApi(String catId, boolean showLoader) {
        final String url = ApiRoutes.GET_SUBCATEGORIES + "?category_id=" + catId;
        if (showLoader) {
            LoadingDialog.showLoading(this, "Loading subcategories...");
        }

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    try {
                        JSONObject root = new JSONObject(response);

                        boolean ok = "success".equalsIgnoreCase(root.optString("status"))
                                || root.optBoolean("ok", false)
                                || root.optBoolean("success", false)
                                || root.optInt("success", 0) == 1;

                        if (!ok) {
                            String msg = root.optString("message", "Failed to load subcategories.");
                            Log.e(TAG, "Subcategories ok=false message=" + msg);
                            if (showLoader) {
                                LoadingDialog.hideLoading();
                                toast(I18n.t(this, msg));
                            }
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        if (dataArr == null)
                            dataArr = root.optJSONArray("subcategories");

                        List<Subcategory> subs = new ArrayList<>();
                        List<CategoryCache.CachedSubcategory> toCache = new ArrayList<>();

                        if (dataArr != null) {
for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.optJSONObject(i);
                                if (obj == null)
                                    continue;

                                String id = firstNonEmpty(
                                        obj.optString("id", ""),
                                        obj.optString("subcategory_id", ""),
                                        String.valueOf(obj.optInt("id", 0)),
                                        String.valueOf(obj.optInt("subcategory_id", 0))).trim();

                                String name = firstNonEmpty(
                                        obj.optString("name", ""),
                                        obj.optString("subcategory_name", "")).trim();

                                String iconUrl = firstNonEmpty(
                                        obj.optString("icon", ""),
                                        obj.optString("icon_url", "")).trim();

                                boolean requiresCond = obj.optInt("hasNewOld", obj.optInt("has_new_old", 0)) == 1;

                                if (!TextUtils.isEmpty(id) && !"0".equals(id) && !TextUtils.isEmpty(name)) {
                                    subs.add(new Subcategory(id, catId, name, iconUrl, requiresCond));
                                    toCache.add(new CategoryCache.CachedSubcategory(id, catId, name, iconUrl,
                                            requiresCond));
}
                            }
                        }

                        categoryCache.saveSubcategories(catId, toCache);
                        subMap.put(catId, subs);
                        if (showLoader)
                            LoadingDialog.hideLoading();
                        displaySubcategories(subs);

                    } catch (JSONException e) {
                        Log.e(TAG, "Subcategories parse error", e);
                        if (showLoader) {
                            LoadingDialog.hideLoading();
                            toast(I18n.t(this, "Parsing error (subcategories)."));
                        }
                    }
                },
                error -> {
                    if (showLoader) {
                        LoadingDialog.hideLoading();
                        toast(I18n.t(this, "Unable to load subcategories. Please check internet."));
                    }
                    Log.e(TAG, "Subcategories request failed: " + describeVolleyError(error));
                }) {
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                h.put("Accept-Language", I18n.lang(CategorySelectActivity.this));
                return h;
            }
        };

        req.setShouldCache(false);
        req.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void updateCtaState() {
        boolean hasCat = selectedCategory != null;
        boolean hasSub = selectedSub != null;
        boolean needCond = false;

        if (hasCat && hasSub) {
            needCond = (selectedSub.requiresCondition != null)
                    ? selectedSub.requiresCondition
                    : selectedCategory.requiresCondition;
        }
        boolean condOk = !needCond || (selectedCondition != null);

        if (btnContinue != null) {
            btnContinue.setEnabled(hasCat && hasSub && condOk);
        }
}

    private List<CategoryGridAdapter.Item> mapToCategoryItems(List<Category> list) {
        List<CategoryGridAdapter.Item> out = new ArrayList<>();
        for (Category c : list) {
            String displayName = I18n.t(this, c.name);
            out.add(new CategoryGridAdapter.Item(
                    c.id,
                    displayName,
                    c.iconUrl,
                    c.requiresCondition));
        }
        return out;
    }

    private void addGridSpacing(RecyclerView rv, int dp) {
        int px = Math.round(getResources().getDisplayMetrics().density * dp);
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent,
                    RecyclerView.State state) {
                outRect.left = px / 2;
                outRect.right = px / 2;
                outRect.top = px / 2;
                outRect.bottom = px / 2;
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static String safeHead(String s) {
        if (s == null)
            return "null";
        s = s.replace("\n", " ").replace("\r", " ");
        return s.length() <= 400 ? s : s.substring(0, 400) + "…";
    }

    private static String firstNonEmpty(String... vals) {
        if (vals == null)
            return "";
        for (String v : vals) {
            if (v != null && !v.trim().isEmpty() && !"0".equals(v.trim()))
                return v;
        }
        return "";
    }

    private static String describeVolleyError(VolleyError err) {
        if (err == null)
            return "VolleyError=null";
        StringBuilder sb = new StringBuilder();
        sb.append(err.getClass().getSimpleName());
        if (err.getMessage() != null)
            sb.append(" msg=").append(err.getMessage());

        NetworkResponse nr = err.networkResponse;
        if (nr != null) {
            sb.append(" status=").append(nr.statusCode);
            if (nr.data != null && nr.data.length > 0) {
                String body = new String(nr.data, StandardCharsets.UTF_8);
                sb.append(" body=").append(body.length() <= 500 ? body : body.substring(0, 500) + "…");
            }
        }
        return sb.toString();
    }

    // Models
    static class Category {
        final String id;
        final String name;
        final String iconUrl;
        final boolean requiresCondition;

        Category(String id, String name, String iconUrl, boolean requiresCondition) {
            this.id = id;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }

    static class Subcategory {
        final String id;
        final String parentId;
        final String name;
        final String iconUrl;
        final Boolean requiresCondition;

        Subcategory(String id, String parentId, String name, String iconUrl, @Nullable Boolean requiresCondition) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }
}
