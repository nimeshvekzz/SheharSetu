package com.infowave.sheharsetu;

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
import com.infowave.sheharsetu.Adapter.CategoryGridAdapter;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.Adapter.SubcategoryGridAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

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
    private String selectedCondition = null; // "new" / "used" / null

    private CategoryGridAdapter categoryAdapter;
    private SubcategoryGridAdapter subcategoryAdapter;

    // === Locale prefs (same as LanguageSelection / I18n) ===
    private static final String PREFS = LanguageSelection.PREFS; // "sheharsetu_prefs"
    private static final String KEY_LANG = LanguageSelection.KEY_LANG_CODE; // "app_lang_code"

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

        Log.d(TAG, "onCreate() started");
        Log.d(TAG, "GET_CATEGORIES=" + ApiRoutes.GET_CATEGORIES);
        Log.d(TAG, "GET_SUBCATEGORIES=" + ApiRoutes.GET_SUBCATEGORIES);

        bindViews();
        prefetchAndApplyStaticTexts();
        setupLists();
        setupClicks();

        loadCategoriesFromApi();
        updateCtaState();
    }

    private void applySavedLocale() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "en");
        Log.d(TAG, "applySavedLocale lang=" + lang);
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

        Log.d(TAG, "bindViews: rvCategories=" + (rvCategories != null)
                + " rvSubcategories=" + (rvSubcategories != null)
                + " conditionRow=" + (conditionRow != null)
                + " cgCondition=" + (cgCondition != null)
                + " btnContinue=" + (btnContinue != null));
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
            Log.d(TAG, "Category clicked: id=" + item.id + " name=" + item.name
                    + " requiresCondition=" + item.requiresCondition);

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
            Log.d(TAG, "Subcategory clicked: id=" + s.id + " name=" + s.name
                    + " requiresCondition=" + s.requiresCondition);

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

                Log.d(TAG, "Condition selected=" + selectedCondition);
                updateCtaState();
            });
        }

        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> {
                Log.d(TAG, "Continue clicked");

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

                Log.d(TAG, "Starting DynamicFormActivity extras: catId=" + selectedCategory.id
                        + " subId=" + selectedSub.id + " condition=" + selectedCondition);

                startActivity(intent);
            });
        }
    }

    private void loadCategoriesFromApi() {
        final String url = ApiRoutes.GET_CATEGORIES;
        Log.d(TAG, "========== LOAD CATEGORIES FROM API START ==========");
        Log.d(TAG, "loadCategoriesFromApi URL=" + url);
        Log.d(TAG, "loadCategoriesFromApi timestamp=" + System.currentTimeMillis());

        LoadingDialog.showLoading(this, "Loading categories...");

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    Log.d(TAG, "========== CATEGORIES API RESPONSE ==========");
                    Log.d(TAG, "Categories raw response len=" + (response == null ? 0 : response.length()));
                    Log.d(TAG, "Categories raw response head=" + safeHead(response));

                    try {
                        JSONObject root = new JSONObject(response);

                        // ✅ accept multiple formats
                        boolean ok = "success".equalsIgnoreCase(root.optString("status"))
                                || root.optBoolean("ok", false)
                                || root.optBoolean("success", false)
                                || root.optInt("success", 0) == 1;

                        if (!ok) {
                            String msg = root.optString("message", "Failed to load categories.");
                            Log.e(TAG, "Categories ok=false message=" + msg);
                            toast(I18n.t(this, msg));
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        if (dataArr == null)
                            dataArr = root.optJSONArray("categories");

                        categories.clear();
                        List<String> catNameKeys = new ArrayList<>();

                        if (dataArr != null) {
                            Log.d(TAG, "Categories dataArr.length=" + dataArr.length());
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.optJSONObject(i);
                                if (obj == null) {
                                    Log.w(TAG, "Category[" + i + "] is null, skipping");
                                    continue;
                                }

                                Log.d(TAG, "--- Category[" + i + "] RAW JSON: " + obj.toString());

                                // ✅ tolerate key variations
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
                                    categories.add(new Category(id, name, iconUrl, requiresCond));
                                    catNameKeys.add(name);
                                    Log.d(TAG, "Category[" + i + "] ADDED: id=" + id + " name=" + name);
                                    Log.d(TAG, "Category[" + i + "] icon field=" + obj.optString("icon", "<empty>"));
                                    Log.d(TAG, "Category[" + i + "] icon_url field="
                                            + obj.optString("icon_url", "<empty>"));
                                    Log.d(TAG, "Category[" + i + "] FINAL iconUrl=" + iconUrl);
                                    Log.d(TAG, "Category[" + i + "] requiresCond=" + requiresCond);
                                } else {
                                    Log.w(TAG, "Skipping category idx=" + i + " id=" + id + " name=" + name);
                                }
                            }
                        } else {
                            Log.e(TAG, "Categories dataArr is NULL!");
                        }

                        Log.d(TAG, "Parsed categories count=" + categories.size());
                        Log.d(TAG, "========== LOAD CATEGORIES COMPLETE ==========");

                        I18n.prefetch(this, catNameKeys, () -> {
                            LoadingDialog.hideLoading();
                            categoryAdapter.submit(mapToCategoryItems(categories));
                            Log.d(TAG, "Category adapter submitted items=" + categories.size());
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Categories parse error", e);
                        toast(I18n.t(this, "Parsing error (categories)."));
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "Categories request failed: " + describeVolleyError(error));
                    toast(I18n.t(this, "Unable to load categories. Please check internet."));
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

    private void loadSubcategories(String catId) {
        Log.d(TAG, "loadSubcategories catId=" + catId);

        if (subMap.containsKey(catId)) {
            List<Subcategory> cached = subMap.get(catId);
            Log.d(TAG, "Using cached subcategories size=" + (cached == null ? 0 : cached.size()));

            List<SubcategoryGridAdapter.Item> ui = new ArrayList<>();
            if (cached != null) {
                for (Subcategory s : cached) {
                    ui.add(new SubcategoryGridAdapter.Item(
                            s.id,
                            s.parentId,
                            I18n.t(this, s.name),
                            s.iconUrl,
                            s.requiresCondition));
                }
            }
            subcategoryAdapter.submit(ui);
            return;
        }

        final String url = ApiRoutes.GET_SUBCATEGORIES + "?category_id=" + catId;
        Log.d(TAG, "========== LOAD SUBCATEGORIES START ==========");
        Log.d(TAG, "Subcategories URL=" + url);
        Log.d(TAG, "Subcategories catId=" + catId);
        Log.d(TAG, "Subcategories timestamp=" + System.currentTimeMillis());

        LoadingDialog.showLoading(this, "Loading subcategories...");

        StringRequest req = new StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    Log.d(TAG, "========== SUBCATEGORIES API RESPONSE ==========");
                    Log.d(TAG, "Subcategories raw response len=" + (response == null ? 0 : response.length()));
                    Log.d(TAG, "Subcategories raw response head=" + safeHead(response));

                    try {
                        JSONObject root = new JSONObject(response);

                        boolean ok = "success".equalsIgnoreCase(root.optString("status"))
                                || root.optBoolean("ok", false)
                                || root.optBoolean("success", false)
                                || root.optInt("success", 0) == 1;

                        if (!ok) {
                            String msg = root.optString("message", "Failed to load subcategories.");
                            Log.e(TAG, "Subcategories ok=false message=" + msg);
                            toast(I18n.t(this, msg));
                            return;
                        }

                        JSONArray dataArr = root.optJSONArray("data");
                        if (dataArr == null)
                            dataArr = root.optJSONArray("subcategories");

                        List<Subcategory> subs = new ArrayList<>();
                        List<String> subNameKeys = new ArrayList<>();

                        if (dataArr != null) {
                            Log.d(TAG, "Subcategories dataArr.length=" + dataArr.length());
                            for (int i = 0; i < dataArr.length(); i++) {
                                JSONObject obj = dataArr.optJSONObject(i);
                                if (obj == null) {
                                    Log.w(TAG, "Subcategory[" + i + "] is null, skipping");
                                    continue;
                                }

                                Log.d(TAG, "--- Subcategory[" + i + "] RAW JSON: " + obj.toString());

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
                                    Subcategory s = new Subcategory(id, catId, name, iconUrl, requiresCond);
                                    subs.add(s);
                                    subNameKeys.add(name);
                                    Log.d(TAG, "Subcategory[" + i + "] ADDED: id=" + id + " name=" + name);
                                    Log.d(TAG, "Subcategory[" + i + "] icon field=" + obj.optString("icon", "<empty>"));
                                    Log.d(TAG, "Subcategory[" + i + "] icon_url field="
                                            + obj.optString("icon_url", "<empty>"));
                                    Log.d(TAG, "Subcategory[" + i + "] FINAL iconUrl=" + iconUrl);
                                    Log.d(TAG, "Subcategory[" + i + "] requiresCond=" + requiresCond);
                                } else {
                                    Log.w(TAG, "Skipping subcategory idx=" + i + " id=" + id + " name=" + name);
                                }
                            }
                        } else {
                            Log.e(TAG, "Subcategories dataArr is NULL!");
                        }

                        subMap.put(catId, subs);
                        Log.d(TAG, "Parsed subcategories count=" + subs.size());
                        Log.d(TAG, "========== LOAD SUBCATEGORIES COMPLETE ==========");

                        I18n.prefetch(this, subNameKeys, () -> {
                            LoadingDialog.hideLoading();
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
                            Log.d(TAG, "Subcategory adapter submitted items=" + ui.size());
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Subcategories parse error", e);
                        toast(I18n.t(this, "Parsing error (subcategories)."));
                    }
                },
                error -> {
                    LoadingDialog.hideLoading();
                    Log.e(TAG, "Subcategories request failed: " + describeVolleyError(error));
                    toast(I18n.t(this, "Unable to load subcategories. Please check internet."));
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

        Log.d(TAG, "CTA state: hasCat=" + hasCat + " hasSub=" + hasSub
                + " needCond=" + needCond + " condOk=" + condOk
                + " selectedCondition=" + selectedCondition);
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
