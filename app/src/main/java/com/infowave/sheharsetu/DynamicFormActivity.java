package com.infowave.sheharsetu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.infowave.sheharsetu.Adapter.DynamicFormAdapter;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DynamicFormActivity extends AppCompatActivity implements DynamicFormAdapter.Callbacks {

    private static final String TAG = "DynamicFormActivity";

    public static final String EXTRA_CATEGORY = "categoryName";
    public static final String RESULT_JSON    = "formResultJson";

    private TextView tvTitle;
    private RecyclerView rvForm;
    private Button btnSubmit;

    private DynamicFormAdapter adapter;

    private String currentPhotoFieldKey;
    private String pendingLocationFieldKey;

    private FusedLocationProviderClient fused;

    // user + category info (for create_listing.php)
    private long userId;
    private long categoryId;
    private long subcategoryId;

    // Remember category name for title
    private String categoryName;

    /* ---------------- Photo pickers ---------------- */

    private final ActivityResultLauncher<String> coverPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                Log.d(TAG, "coverPicker result: " + uri + " for fieldKey=" + currentPhotoFieldKey);
                if (uri != null && currentPhotoFieldKey != null && adapter != null) {
                    String base64 = encodeImageToBase64(uri);
                    if (base64 != null) {
                        adapter.setCoverPhoto(currentPhotoFieldKey, base64);
                    } else {
                        toast("Failed to read selected image");
                    }
                }
            });

    private final ActivityResultLauncher<String> morePicker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                Log.d(TAG, "morePicker result size=" + (uris == null ? 0 : uris.size()) +
                        " for fieldKey=" + currentPhotoFieldKey);
                if (uris != null && currentPhotoFieldKey != null && adapter != null) {
                    java.util.ArrayList<String> base64List = new java.util.ArrayList<>();
                    for (Uri u : uris) {
                        if (u == null) continue;
                        String b64 = encodeImageToBase64(u);
                        if (b64 != null) {
                            base64List.add(b64);
                        }
                    }
                    if (!base64List.isEmpty()) {
                        adapter.addMorePhotos(currentPhotoFieldKey, base64List);
                    } else {
                        toast("Failed to read selected images");
                    }
                }
            });

    /* ---------------- Permissions ---------------- */

    private final ActivityResultLauncher<String> locationPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                Log.d(TAG, "Location permission result: " + granted +
                        " for fieldKey=" + pendingLocationFieldKey);
                if (granted) fillMyLocation(pendingLocationFieldKey);
                else toast("Location permission denied");
            });

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_form);
        applyThemeBarsAndWidgets();

        tvTitle   = findViewById(R.id.tvTitle);
        rvForm    = findViewById(R.id.rvForm);
        btnSubmit = findViewById(R.id.btnSubmit);

        // XML me agar enabled=false hai to bhi yahan se control lenge
        btnSubmit.setEnabled(false);

        fused = LocationServices.getFusedLocationProviderClient(this);

        Intent intent = getIntent();

        // category name (UI only)
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = "General";
        categoryName = category;
        tvTitle.setText("Dynamic Form (" + categoryName + ")");

        // --------- READ category_id / subcategory_id safely (String or Long) ----------
        String catIdStr = intent.getStringExtra("category_id");
        String subIdStr = intent.getStringExtra("subcategory_id");

        Log.d(TAG, "Raw extras: category_id(str)=" + catIdStr + ", subcategory_id(str)=" + subIdStr);

        // if coming as String (current case)
        categoryId = parseLongSafe(catIdStr);
        subcategoryId = parseLongSafe(subIdStr);

        // if future me Long ke form me bhejo, extra safety:
        if (categoryId == 0) {
            long tmp = intent.getLongExtra("category_id", 0L);
            if (tmp != 0L) {
                Log.d(TAG, "category_id also found as Long extra: " + tmp);
                categoryId = tmp;
            }
        }
        if (subcategoryId == 0) {
            long tmp = intent.getLongExtra("subcategory_id", 0L);
            if (tmp != 0L) {
                Log.d(TAG, "subcategory_id also found as Long extra: " + tmp);
                subcategoryId = tmp;
            }
        }

        // ✅ user_id from SharedPreferences (set after login/OTP verify)
        //    Same prefs file as SplashScreen / LoginActivity
        SharedPreferences prefs = getSharedPreferences(SplashScreen.PREFS, MODE_PRIVATE);
        userId = prefs.getLong("user_id", 0L);

        Log.d(TAG, "onCreate: categoryName=" + categoryName +
                " categoryId=" + categoryId +
                " subcategoryId=" + subcategoryId +
                " userId=" + userId);

        rvForm.setLayoutManager(new LinearLayoutManager(this));

        // Load schema from server (DB-based) ONLY.
        loadSchemaFromServer(categoryId, subcategoryId);

        btnSubmit.setOnClickListener(v -> {
            Log.d(TAG, "Submit clicked");
            toast("Submit clicked"); // sirf debug ke liye, baad me hata sakte ho

            if (adapter == null) {
                toast("Form is not ready yet, please wait...");
                Log.e(TAG, "Submit pressed but adapter is null");
                return;
            }
            JSONObject result = adapter.validateAndBuildResult();
            if (result == null) {
                toast("Please complete required fields correctly.");
                Log.e(TAG, "validateAndBuildResult() returned null");
                return;
            }
            Log.d(TAG, "validateAndBuildResult() success: " + result.toString());
            submitListing(result);
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        WindowInsetsControllerCompat wic =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    bars.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return insets;
        });
    }

    private long parseLongSafe(String s) {
        if (TextUtils.isEmpty(s)) return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            Log.w(TAG, "parseLongSafe failed for '" + s + "' : " + e.getMessage());
            return 0L;
        }
    }

    /**
     * Load form schema from backend (get_form_schema.php) using categoryId + subcategoryId.
     * Yahin par hum decide karenge ki is_new SWITCH dikhana hai ya nahi.
     */
    private void loadSchemaFromServer(long categoryId, long subcategoryId) {
        Log.d(TAG, "loadSchemaFromServer() called with categoryId=" + categoryId +
                ", subcategoryId=" + subcategoryId + ", categoryName=" + categoryName);

        if (categoryId <= 0) {
            String msg = "Category info missing (categoryId<=0). Cannot load dynamic schema.";
            Log.e(TAG, msg);
            toast(msg);
            btnSubmit.setEnabled(false);
            return;
        }

        StringBuilder urlBuilder = new StringBuilder(ApiRoutes.GET_FORM_SCHEMA);
        urlBuilder.append("?category_id=").append(categoryId);
        if (subcategoryId > 0) {
            urlBuilder.append("&subcategory_id=").append(subcategoryId);
        }
        urlBuilder.append("&lang=en");

        String url = urlBuilder.toString();
        Log.d(TAG, "Requesting schema from URL: " + url);

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        Log.d(TAG, "Schema raw response: " + response.toString());

                        boolean success = response.optBoolean("success", false);
                        String msg = response.optString("message", "");
                        Log.d(TAG, "Schema success=" + success + " message=" + msg);

                        if (!success) {
                            toast(TextUtils.isEmpty(msg) ? "Failed to load form schema" : msg);
                            Log.e(TAG, "Backend returned success=false for schema, aborting.");
                            btnSubmit.setEnabled(false);
                            return;
                        }

                        JSONObject data = response.optJSONObject("data");
                        if (data == null) {
                            toast("Invalid schema response (data=null).");
                            Log.e(TAG, "Response data is null");
                            btnSubmit.setEnabled(false);
                            return;
                        }

                        JSONObject catObj = data.optJSONObject("category");
                        JSONObject subObj = data.optJSONObject("subcategory");
                        Log.d(TAG, "Category from server: " + (catObj == null ? "null" : catObj.toString()));
                        Log.d(TAG, "Subcategory from server: " + (subObj == null ? "null" : subObj.toString()));

                        // 🔥 NEW: decide if this subcategory supports NEW/OLD condition switch
                        boolean supportsCondition = false;
                        if (subObj != null) {
                            // Try int or boolean dono handle karo
                            int scInt = subObj.optInt("supports_condition", -1);
                            boolean scBool = subObj.optBoolean("supports_condition", false);
                            if (scInt == 1 || scBool) {
                                supportsCondition = true;
                            }
                        }
                        // Fallback: agar subcategory me nahi ho to category se dekh lo
                        if (!supportsCondition && catObj != null) {
                            int scInt = catObj.optInt("supports_condition", -1);
                            boolean scBool = catObj.optBoolean("supports_condition", false);
                            if (scInt == 1 || scBool) {
                                supportsCondition = true;
                            }
                        }
                        Log.d(TAG, "supportsCondition (NEW/USED switch allowed) = " + supportsCondition);

                        JSONArray schemaArr = data.optJSONArray("schema");
                        if (schemaArr == null) {
                            toast("No schema array returned.");
                            Log.e(TAG, "schemaArr is null");
                            btnSubmit.setEnabled(false);
                            return;
                        }

                        Log.d(TAG, "Schema array length: " + schemaArr.length());

                        java.util.ArrayList<Map<String, Object>> schema = new java.util.ArrayList<>();

                        for (int i = 0; i < schemaArr.length(); i++) {
                            JSONObject field = schemaArr.optJSONObject(i);
                            if (field == null) {
                                Log.w(TAG, "schemaArr[" + i + "] is null, skipping");
                                continue;
                            }

                            String key   = field.optString("key", "");
                            String label = field.optString("label", "");
                            String hint  = field.optString("hint", "");
                            String type  = field.optString("type", "TEXT");
                            boolean required = field.optBoolean("required", false);
                            String unit  = field.optString("unit", "");

                            // 🔥 IMPORTANT:
                            // Agar yeh is_new field hai aur supportsCondition = false,
                            // to is field ko UI me show hi nahi karna.
                            if ("is_new".equalsIgnoreCase(key) && !supportsCondition) {
                                Log.d(TAG, "Skipping field 'is_new' because supports_condition = false for this subcategory");
                                continue;
                            }

                            Map<String, Object> m = new HashMap<>();
                            m.put("key",      key);
                            m.put("label",    label);
                            m.put("hint",     hint);
                            m.put("type",     type);
                            m.put("required", required);
                            m.put("unit",     unit);

                            JSONArray optsArr = field.optJSONArray("options");
                            if (optsArr != null && optsArr.length() > 0) {
                                java.util.ArrayList<Map<String, Object>> opts = new java.util.ArrayList<>();
                                for (int j = 0; j < optsArr.length(); j++) {
                                    JSONObject opt = optsArr.optJSONObject(j);
                                    if (opt == null) {
                                        Log.w(TAG, "options[" + j + "] for field " + key + " is null");
                                        continue;
                                    }
                                    Map<String, Object> om = new HashMap<>();
                                    String oVal = opt.optString("value", "");
                                    String oLab = opt.optString("label", "");
                                    om.put("value", oVal);
                                    om.put("label", oLab);
                                    opts.add(om);
                                }
                                m.put("options", opts);
                                Log.d(TAG, "Field[" + i + "] key=" + key + " type=" + type +
                                        " required=" + required +
                                        " optionsCount=" + opts.size());
                            } else {
                                Log.d(TAG, "Field[" + i + "] key=" + key + " type=" + type +
                                        " required=" + required + " optionsCount=0");
                            }
                            schema.add(m);
                        }

                        Log.d(TAG, "Final schema list size after parsing (after is_new filter) = " + schema.size());

                        if (schema.isEmpty()) {
                            toast("Empty schema received from server.");
                            Log.e(TAG, "Schema list is empty, not setting adapter");
                            btnSubmit.setEnabled(false);
                        } else {
                            adapter = new DynamicFormAdapter(schema, this);
                            rvForm.setAdapter(adapter);
                            Log.d(TAG, "Adapter set with itemCount=" + adapter.getItemCount());
                            // Ab form ready hai, button enable karo
                            btnSubmit.setEnabled(true);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Schema parse error", e);
                        toast("Error parsing schema.");
                        btnSubmit.setEnabled(false);
                    }
                },
                error -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Schema request error: ").append(error.toString());
                    if (error.networkResponse != null) {
                        sb.append(" statusCode=").append(error.networkResponse.statusCode);
                        if (error.networkResponse.data != null) {
                            sb.append(" body=")
                                    .append(new String(error.networkResponse.data));
                        }
                    }
                    Log.e(TAG, sb.toString());
                    toast("Unable to load form. Please try again.");
                    btnSubmit.setEnabled(false);
                }
        );

        VolleySingleton.getInstance(this).add(req);
    }

    /* ================== Callbacks from Adapter ================== */

    @Override
    public void pickCoverPhoto(String fieldKey) {
        Log.d(TAG, "pickCoverPhoto called for key=" + fieldKey);
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        coverPicker.launch("image/*");
    }

    @Override
    public void pickMorePhotos(String fieldKey) {
        Log.d(TAG, "pickMorePhotos called for key=" + fieldKey);
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        morePicker.launch("image/*");
    }

    @Override
    public void requestMyLocation(String fieldKey) {
        Log.d(TAG, "requestMyLocation called for key=" + fieldKey);
        pendingLocationFieldKey = fieldKey;
        locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Override
    public void showToast(String msg) {
        toast(msg);
    }

    /* ================== Networking: submit listing ================== */

    private void submitListing(JSONObject formResult) {
        Log.d(TAG, "submitListing() called, userId=" + userId +
                " categoryId=" + categoryId + " subcategoryId=" + subcategoryId);

        long effectiveUserId = userId;

        if (effectiveUserId <= 0) {
            toast("User not logged in. Please login again.");
            Log.e(TAG, "submitListing: effectiveUserId<=0");
            return;
        }
        if (categoryId <= 0) {
            toast("Category information missing.");
            Log.e(TAG, "submitListing: categoryId<=0");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("user_id", effectiveUserId);
            payload.put("category_id", categoryId);
            if (subcategoryId > 0) payload.put("subcategory_id", subcategoryId);

            String title = buildTitleFromForm(formResult);
            payload.put("title", title);
            payload.put("form_data", formResult);

            // 🔥 NEW/USED handling: read from dynamic form (boolean SWITCH field "is_new")
            int isNewValue = 0; // default = used
            try {
                if (formResult.has("is_new")) {
                    Object v = formResult.get("is_new");
                    if (v instanceof Boolean) {
                        isNewValue = ((Boolean) v) ? 1 : 0;
                    } else {
                        String s = String.valueOf(v).trim();
                        if ("1".equals(s) ||
                                "true".equalsIgnoreCase(s) ||
                                "yes".equalsIgnoreCase(s) ||
                                "new".equalsIgnoreCase(s)) {
                            isNewValue = 1;
                        } else {
                            isNewValue = 0;
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing is_new from formResult, defaulting to 0", e);
                isNewValue = 0;
            }
            payload.put("is_new", isNewValue);
            Log.d(TAG, "submitListing: resolved is_new=" + isNewValue);

            Log.d(TAG, "submitListing payload: " + payload.toString());

            btnSubmit.setEnabled(false);
            toast("Submitting your form...");

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    ApiRoutes.CREATE_LISTING,
                    payload,
                    response -> {
                        btnSubmit.setEnabled(true);
                        Log.d(TAG, "submitListing response: " + response.toString());

                        boolean success = response.optBoolean("success", false);
                        String message = response.optString("message",
                                success ? "Listing created" : "Failed to create listing");

                        toast(message);

                        if (success) {
                            Log.d(TAG, "Listing created successfully, opening MainActivity");
                            Intent i = new Intent(DynamicFormActivity.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            Log.e(TAG, "Listing creation failed");
                        }
                    },
                    error -> {
                        btnSubmit.setEnabled(true);
                        StringBuilder sb = new StringBuilder();
                        sb.append("submitListing error: ").append(error.toString());
                        if (error.networkResponse != null) {
                            sb.append(" statusCode=").append(error.networkResponse.statusCode);
                            if (error.networkResponse.data != null) {
                                sb.append(" body=")
                                        .append(new String(error.networkResponse.data));
                            }
                        }
                        Log.e(TAG, sb.toString());
                        String errorMsg = "Server error";
                        if (error.networkResponse != null) {
                            errorMsg = "Error " + error.networkResponse.statusCode;
                        }
                        toast(errorMsg);
                    }
            );

            VolleySingleton.getInstance(this).add(req);

        } catch (Exception e) {
            btnSubmit.setEnabled(true);
            Log.e(TAG, "Error preparing request", e);
            toast("Error preparing request: " + e.getMessage());
        }
    }

    /** Title helper */
    private String buildTitleFromForm(JSONObject form) {
        try {
            String brand   = form.optString("brand", "").trim();
            String model   = form.optString("model", "").trim();
            String year    = form.optString("year", "").trim();
            String product = form.optString("product", "").trim();

            StringBuilder sb = new StringBuilder();
            if (!brand.isEmpty()) sb.append(brand);
            if (!model.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(model);
            }
            if (!year.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(year);
            }
            if (sb.length() == 0 && !product.isEmpty()) {
                sb.append(product);
            }
            if (sb.length() == 0) {
                sb.append("Listing");
            }
            String title = sb.toString();
            Log.d(TAG, "buildTitleFromForm => " + title);
            return title;
        } catch (Exception e) {
            Log.e(TAG, "buildTitleFromForm error", e);
            return "Listing";
        }
    }

    /* ================== Helpers ================== */

    private void requestReadPhotoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            Log.d(TAG, "No READ_EXTERNAL_STORAGE permission required (SDK>=33)");
        } else {
            Log.d(TAG, "Requesting READ_EXTERNAL_STORAGE permission");
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1231);
        }
    }

    private void fillMyLocation(String fieldKey) {
        Log.d(TAG, "fillMyLocation() for fieldKey=" + fieldKey);
        if (fieldKey == null || adapter == null) {
            Log.e(TAG, "fillMyLocation aborted: fieldKey or adapter is null");
            return;
        }
        try {
            fused.getLastLocation().addOnSuccessListener(loc -> {
                if (loc == null) {
                    toast("Unable to fetch location");
                    Log.e(TAG, "getLastLocation returned null");
                    return;
                }
                try {
                    Geocoder geo = new Geocoder(this, Locale.getDefault());
                    java.util.List<Address> res = geo.getFromLocation(
                            loc.getLatitude(), loc.getLongitude(), 1);
                    String addr;
                    if (res != null && !res.isEmpty()) {
                        Address a = res.get(0);
                        String locality = a.getLocality() == null ? "" : a.getLocality();
                        String admin    = a.getAdminArea() == null ? "" : a.getAdminArea();
                        addr = (locality + (admin.isEmpty() ? "" : ", " + admin)).trim();
                        if (addr.isEmpty()) addr = a.getFeatureName();
                    } else {
                        addr = loc.getLatitude() + "," + loc.getLongitude();
                    }
                    Log.d(TAG, "Resolved location: " + addr);
                    adapter.setTextAnswer(fieldKey, addr);
                    toast("Location set");
                } catch (Exception e) {
                    Log.e(TAG, "Geocoder failed", e);
                    toast("Geocoder failed");
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in fillMyLocation", e);
        }
    }

    /**
     * Convert selected image URI into Base64 (JPEG, resized if very large).
     */
    private String encodeImageToBase64(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Log.e(TAG, "encodeImageToBase64: openInputStream returned null for uri=" + uri);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (bitmap == null) {
                Log.e(TAG, "encodeImageToBase64: bitmap is null for uri=" + uri);
                return null;
            }

            // Optional: resize if too large
            int maxSize = 1280;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxSize || height > maxSize) {
                float scale = Math.min(
                        (float) maxSize / width,
                        (float) maxSize / height
                );
                int newW = Math.round(width * scale);
                int newH = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

            Log.d(TAG, "encodeImageToBase64: size=" + bytes.length + " bytes for uri=" + uri);
            return base64;

        } catch (Exception e) {
            Log.e(TAG, "encodeImageToBase64 failed for uri=" + uri, e);
            return null;
        }
    }

    private void applyThemeBarsAndWidgets() {
        try {
            getWindow().setStatusBarColor(
                    ContextCompat.getColor(this, R.color.black));
            getWindow().setNavigationBarColor(
                    ContextCompat.getColor(this, R.color.black));
            getWindow().getDecorView().setSystemUiVisibility(0);
        } catch (Exception ignored) { }

        View root = findViewById(R.id.root);
        if (root != null) {
            root.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.ss_surface));
        }

        try {
            androidx.appcompat.widget.Toolbar tb = findViewById(R.id.topBar);
            if (tb != null) {
                tb.setBackgroundColor(
                        ContextCompat.getColor(this, R.color.ss_primary));
                tb.setTitleTextColor(
                        ContextCompat.getColor(this, android.R.color.white));
                if (tb.getNavigationIcon() != null) {
                    tb.getNavigationIcon().setTint(
                            ContextCompat.getColor(this, android.R.color.white));
                }
            }
        } catch (Exception ignored) { }

        try {
            Button btn = findViewById(R.id.btnSubmit);
            if (btn != null) {
                btn.setBackground(
                        ContextCompat.getDrawable(this, R.drawable.bg_btn_primary));
                btn.setTextColor(
                        ContextCompat.getColor(this, android.R.color.white));
            }
        } catch (Exception ignored) { }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /* NOTE:
       Static buildSchema / fallback completely removed.
       Ab sirf DB se dynamic schema hi chalega.
    */
}
