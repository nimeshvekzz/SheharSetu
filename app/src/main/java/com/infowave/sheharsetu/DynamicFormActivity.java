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
import android.content.pm.PackageManager;

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
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

import org.json.JSONArray;
import org.json.JSONObject;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import androidx.activity.result.IntentSenderRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DynamicFormActivity extends AppCompatActivity implements DynamicFormAdapter.Callbacks {

    private static final String TAG = "DynamicFormActivity";

    public static final String EXTRA_CATEGORY = "categoryName";
    public static final String RESULT_JSON = "formResultJson";

    private TextView tvTitle;
    private RecyclerView rvForm;
    private Button btnSubmit;
    private androidx.appcompat.widget.Toolbar toolbar;

    private DynamicFormAdapter adapter;

    private com.google.android.material.textfield.TextInputEditText etAddress;
    private com.google.android.material.textfield.TextInputEditText etVillageCity;
    private com.google.android.material.textfield.TextInputLayout tilVillageCity;
    private com.google.android.material.button.MaterialButton btnDetectLocation;

    private Double detectedLatitude = null;
    private Double detectedLongitude = null;
    private String detectedState = null;
    private String detectedDistrict = null;

    private String currentPhotoFieldKey;
    private String pendingLocationFieldKey;

    private FusedLocationProviderClient fused;

    private SettingsClient settingsClient;
    private LocationSettingsRequest locationSettingsRequest;

    private long userId;
    private long categoryId;
    private long subcategoryId;

    private String categoryName;

    private final ActivityResultLauncher<String> coverPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && currentPhotoFieldKey != null && adapter != null) {
                    String base64 = encodeImageToBase64(uri);
                    if (base64 != null) {
                        adapter.setCoverPhoto(currentPhotoFieldKey, base64);
                    } else {
                        toast("Failed to read selected image");
                    }
                }
            });

    private final ActivityResultLauncher<String> morePicker = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && currentPhotoFieldKey != null && adapter != null) {
                    java.util.ArrayList<String> base64List = new java.util.ArrayList<>();
                    for (Uri u : uris) {
                        if (u == null)
                            continue;
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

    private final ActivityResultLauncher<String> locationPerm = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    checkLocationSettingsAndDetect(pendingLocationFieldKey);
                else
                    toast("Location permission denied");
            });

    private final ActivityResultLauncher<IntentSenderRequest> gpsResolutionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // User enabled GPS, retry detection
                    fetchLocationAndProcess(pendingLocationFieldKey);
                } else {
                    toast("GPS is required to detect location");
                }
            });

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic_form);
        applyThemeBarsAndWidgets();

        tvTitle = findViewById(R.id.tvTitle);
        rvForm = findViewById(R.id.rvForm);
        btnSubmit = findViewById(R.id.btnSubmit);

        // Bind location fields
        etAddress = findViewById(R.id.etAddress);
        etVillageCity = findViewById(R.id.etVillageCity);
        tilVillageCity = findViewById(R.id.tilVillageCity);
        btnDetectLocation = findViewById(R.id.btnDetectLocation);

        // Auto-scroll to location fields when keyboard opens
        View.OnFocusChangeListener scrollToFocus = (v, hasFocus) -> {
            if (hasFocus) {
                // Delay to let the keyboard finish appearing, then scroll into view
                v.postDelayed(() -> {
                    android.graphics.Rect rect = new android.graphics.Rect();
                    v.getDrawingRect(rect);
                    v.requestRectangleOnScreen(rect, false);
                }, 350);
            }
        };
        etAddress.setOnFocusChangeListener(scrollToFocus);
        etVillageCity.setOnFocusChangeListener(scrollToFocus);

        // Setup toolbar with back navigation
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        btnSubmit.setEnabled(false);

        fused = LocationServices.getFusedLocationProviderClient(this);
        setupLocationSettings();

        Intent intent = getIntent();

        // category name (UI only)
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        if (category == null)
            category = "General";
        categoryName = category;
        tvTitle.setText("Sell in " + categoryName);

        String catIdStr = intent.getStringExtra("category_id");
        String subIdStr = intent.getStringExtra("subcategory_id");
        // if coming as String (current case)
        categoryId = parseLongSafe(catIdStr);
        subcategoryId = parseLongSafe(subIdStr);

        // if future me Long ke form me bhejo, extra safety:
        if (categoryId == 0) {
            long tmp = intent.getLongExtra("category_id", 0L);
            if (tmp != 0L) {
                categoryId = tmp;
            }
        }
        if (subcategoryId == 0) {
            long tmp = intent.getLongExtra("subcategory_id", 0L);
            if (tmp != 0L) {
                subcategoryId = tmp;
            }
        }

        SharedPreferences prefs = getSharedPreferences(SplashScreen.PREFS, MODE_PRIVATE);
        userId = prefs.getLong("user_id", 0L);
        rvForm.setLayoutManager(new LinearLayoutManager(this));

        // Load schema from server (DB-based) ONLY.
        loadSchemaFromServer(categoryId, subcategoryId);

        // Detect My Location button
        btnDetectLocation.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                checkLocationSettingsAndDetect(null);
            }
        });

        btnSubmit.setOnClickListener(v ->

        {
            if (adapter == null) {
                toast("Form is not ready yet, please wait...");
                Log.e(TAG, "Submit pressed but adapter is null");
                return;
            }

            // Validate location: Village/City is required
            String villageCityText = etVillageCity.getText().toString().trim();
            if (villageCityText.isEmpty()) {
                tilVillageCity.setError("Please enter village or city name");
                toast("Village/City is required");
                return;
            } else {
                tilVillageCity.setError(null);
            }

            JSONObject result = adapter.validateAndBuildResult();
            if (result == null) {
                toast("Please complete required fields correctly.");
                Log.e(TAG, "validateAndBuildResult() returned null");
                return;
            }
            Log.d(TAG, "Final JSON before submit: " + result.toString());
            submitListing(result);
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);

        WindowInsetsControllerCompat wic = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        wic.setAppearanceLightStatusBars(false);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    bars.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom());
            return insets;
        });
    }

    private long parseLongSafe(String s) {
        if (TextUtils.isEmpty(s))
            return 0L;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private void loadSchemaFromServer(long categoryId, long subcategoryId) {
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
        LoadingDialog.showLoading(this, "Loading form...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        boolean success = response.optBoolean("success", false);
                        String msg = response.optString("message", "");
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

                        if (!supportsCondition && catObj != null) {
                            int scInt = catObj.optInt("supports_condition", -1);
                            boolean scBool = catObj.optBoolean("supports_condition", false);
                            if (scInt == 1 || scBool) {
                                supportsCondition = true;
                            }
                        }
                        JSONArray schemaArr = data.optJSONArray("schema");
                        if (schemaArr == null) {
                            toast("No schema array returned.");
                            Log.e(TAG, "schemaArr is null");
                            btnSubmit.setEnabled(false);
                            return;
                        }
                        java.util.ArrayList<Map<String, Object>> schema = new java.util.ArrayList<>();

                        for (int i = 0; i < schemaArr.length(); i++) {
                            JSONObject field = schemaArr.optJSONObject(i);
                            if (field == null) {
                                continue;
                            }

                            String key = field.optString("key", "");
                            String label = field.optString("label", "");
                            String hint = field.optString("hint", "");
                            String type = field.optString("type", "TEXT");
                            boolean required = field.optBoolean("required", false);
                            String unit = field.optString("unit", "");

                            if ("is_new".equalsIgnoreCase(key) && !supportsCondition) {
                                continue;
                            }

                            Map<String, Object> m = new HashMap<>();
                            m.put("key", key);
                            m.put("label", label);
                            m.put("hint", hint);
                            m.put("type", type);
                            m.put("required", required);
                            m.put("unit", unit);

                            JSONArray optsArr = field.optJSONArray("options");
                            if (optsArr != null && optsArr.length() > 0) {
                                java.util.ArrayList<Map<String, Object>> opts = new java.util.ArrayList<>();
                                for (int j = 0; j < optsArr.length(); j++) {
                                    JSONObject opt = optsArr.optJSONObject(j);
                                    if (opt == null) {
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
                            } else {
                            }
                            schema.add(m);
                        }
                        if (schema.isEmpty()) {
                            toast("Empty schema received from server.");
                            Log.e(TAG, "Schema list is empty, not setting adapter");
                            btnSubmit.setEnabled(false);
                        } else {
                            adapter = new DynamicFormAdapter(schema, this);
                            rvForm.setAdapter(adapter);
                            // Ab form ready hai, button enable karo
                            btnSubmit.setEnabled(true);
                            LoadingDialog.hideLoading();
                        }

                    } catch (Exception e) {
                        LoadingDialog.hideLoading();
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
                    LoadingDialog.hideLoading();
                    toast("Unable to load form. Please try again.");
                    btnSubmit.setEnabled(false);
                }) {

            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Accept", "application/json");
                headers.put("Accept-Language", "en");
                return headers;
            }

        };
        VolleySingleton.getInstance(this).add(req);
    }

    @Override
    public void pickCoverPhoto(String fieldKey) {
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        coverPicker.launch("image/*");
    }

    @Override
    public void pickMorePhotos(String fieldKey) {
        currentPhotoFieldKey = fieldKey;
        requestReadPhotoPermissionIfNeeded();
        morePicker.launch("image/*");
    }

    @Override
    public void requestMyLocation(String fieldKey) {
        pendingLocationFieldKey = fieldKey;
        locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Override
    public void showToast(String msg) {
        toast(msg);
    }

    private void submitListing(JSONObject formResult) {
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

        // Get location text
        String addressText = etAddress.getText().toString().trim();
        String villageCityText = etVillageCity.getText().toString().trim();

        // If GPS was NOT used (detectedLatitude is null) and user typed a location,
        // geocode it on Android side BEFORE sending to backend
        if ((detectedLatitude == null || detectedLongitude == null) && !villageCityText.isEmpty()) {
            btnSubmit.setEnabled(false);
            LoadingDialog.showLoading(this, "Detecting location from text...");

            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    // Add "Gujarat, India" for better accuracy
                    String searchQuery = villageCityText;
                    if (!searchQuery.toLowerCase().contains("gujarat")) {
                        searchQuery += ", Gujarat";
                    }
                    if (!searchQuery.toLowerCase().contains("india")) {
                        searchQuery += ", India";
                    }

                    Log.d(TAG, "Android Geocoder: searching for '" + searchQuery + "'");
                    java.util.List<Address> results = geocoder.getFromLocationName(searchQuery, 1);

                    runOnUiThread(() -> {
                        if (results != null && !results.isEmpty()) {
                            Address addr = results.get(0);
                            detectedLatitude = addr.getLatitude();
                            detectedLongitude = addr.getLongitude();

                            // Also extract state/district if not already set
                            if (detectedState == null || detectedState.isEmpty()) {
                                detectedState = addr.getAdminArea();
                            }
                            if (detectedDistrict == null || detectedDistrict.isEmpty()) {
                                detectedDistrict = addr.getSubAdminArea();
                            }

                            Log.d(TAG, "Android Geocoder: SUCCESS → " + detectedLatitude + ", " + detectedLongitude);
                            toast("Location found: " + addr.getLocality());

                            // Now proceed with actual submission
                            LoadingDialog.hideLoading();
                            doSubmitListing(formResult, addressText, villageCityText);
                        } else {
                            Log.w(TAG, "Android Geocoder: no results for '" + villageCityText + "'");
                            LoadingDialog.hideLoading();
                            // Still proceed — backend has fallback geocoding
                            toast("Location not found precisely, submitting anyway...");
                            doSubmitListing(formResult, addressText, villageCityText);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Android Geocoder failed", e);
                    runOnUiThread(() -> {
                        LoadingDialog.hideLoading();
                        // Proceed anyway — backend will try its own geocoding
                        toast("Geocoding failed, submitting anyway...");
                        doSubmitListing(formResult, addressText, villageCityText);
                    });
                }
            }).start();
        } else {
            // GPS was used or no location text — submit directly
            doSubmitListing(formResult, addressText, villageCityText);
        }
    }

    /**
     * Actually send the listing to the backend (called after geocoding if needed)
     */
    private void doSubmitListing(JSONObject formResult, String addressText, String villageCityText) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("user_id", userId);
            payload.put("category_id", categoryId);
            if (subcategoryId > 0)
                payload.put("subcategory_id", subcategoryId);

            String title = buildTitleFromForm(formResult);
            payload.put("title", title);

            // Add location data to form_data
            if (!addressText.isEmpty()) {
                formResult.put("address", addressText);
            }
            if (!villageCityText.isEmpty()) {
                formResult.put("village_name", villageCityText);
            }

            // Include lat/lng (from GPS or Android-side geocoding)
            if (detectedLatitude != null && detectedLongitude != null) {
                formResult.put("latitude", detectedLatitude);
                formResult.put("longitude", detectedLongitude);
            }
            // Always send state — fallback to "Gujarat" for manual entry (no GPS)
            if (detectedState != null && !detectedState.isEmpty()) {
                formResult.put("state", detectedState);
            } else {
                formResult.put("state", "Gujarat");
            }
            if (detectedDistrict != null && !detectedDistrict.isEmpty()) {
                formResult.put("district", detectedDistrict);
            }

            payload.put("form_data", formResult);

            int isNewValue = 0; // default = used
            if (formResult.has("is_new")) {
                Object v = formResult.opt("is_new");
                if (v instanceof Boolean) {
                    isNewValue = ((Boolean) v) ? 1 : 0;
                } else {
                    String s = String.valueOf(v).trim().toLowerCase();
                    if (s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("new")) {
                        isNewValue = 1;
                    }
                }
            }
            payload.put("is_new", isNewValue);
            btnSubmit.setEnabled(false);
            LoadingDialog.showLoading(this, "Submitting listing...");

            Log.d(TAG, "Final payload: " + payload.toString());

            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    ApiRoutes.CREATE_LISTING,
                    payload,
                    response -> {
                        LoadingDialog.hideLoading();
                        btnSubmit.setEnabled(true);
                        boolean success = response.optBoolean("success", false);
                        String message = response.optString("message",
                                success ? "Listing created" : "Failed to create listing");

                        toast(message);

                        if (success) {
                            Intent i = new Intent(DynamicFormActivity.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            Log.e(TAG, "Listing creation failed");
                        }
                    },
                    error -> {
                        LoadingDialog.hideLoading();
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
                    }) {

                @Override
                public Map<String, String> getHeaders() {
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Accept", "application/json");
                    headers.put("Content-Type", "application/json");

                    // JWT Authorization for secure listing creation
                    SessionManager sm = new SessionManager(DynamicFormActivity.this);
                    String token = sm.getAccessToken();
                    if (token != null && !token.isEmpty()) {
                        headers.put("Authorization", "Bearer " + token);
                    }
                    return headers;
                }
            };

            VolleySingleton.getInstance(this).add(req);

        } catch (Exception e) {
            btnSubmit.setEnabled(true);
            Log.e(TAG, "Error preparing request", e);
            toast("Error preparing request: " + e.getMessage());
        }
    }

    private String buildTitleFromForm(JSONObject form) {
        try {
            String brand = form.optString("brand", "").trim();
            String model = form.optString("model", "").trim();
            String year = form.optString("year", "").trim();
            String product = form.optString("product", "").trim();

            StringBuilder sb = new StringBuilder();
            if (!brand.isEmpty())
                sb.append(brand);
            if (!model.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(model);
            }
            if (!year.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" ");
                sb.append(year);
            }
            if (sb.length() == 0 && !product.isEmpty()) {
                sb.append(product);
            }
            if (sb.length() == 0) {
                sb.append("Listing");
            }
            String title = sb.toString();
            return title;
        } catch (Exception e) {
            Log.e(TAG, "buildTitleFromForm error", e);
            return "Listing";
        }
    }

    private void requestReadPhotoPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, 1231);
        }
    }

    private com.google.android.gms.location.LocationCallback locationCallback;

    @SuppressLint("MissingPermission")
    private void fetchLocationAndProcess(String fieldKey) {
        // 1. Check Perms
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.pendingLocationFieldKey = fieldKey;
            locationPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(500)
                .setNumUpdates(5); // Try 5 updates then stop

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        settingsClient.checkLocationSettings(builder.build())
                .addOnSuccessListener(this, locationSettingsResponse -> {
                    // Settings OK -> Fetch Location
                    startLocationUpdates(fieldKey);
                })
                .addOnFailureListener(this, e -> {
                    if (e instanceof ResolvableApiException) {
                        try {
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            this.pendingLocationFieldKey = fieldKey;
                            IntentSenderRequest isr = new IntentSenderRequest.Builder(resolvable.getResolution())
                                    .build();
                            gpsResolutionLauncher.launch(isr);
                        } catch (Exception sendEx) {
                            // Ignore
                        }
                    } else {
                        toast("GPS is off and cannot be resolved");
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates(String fieldKey) {
        LoadingDialog.showLoading(this, "Detecting location (Please wait)...");

        // Prepare Request
        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setNumUpdates(10) // Give it a few tries
                .setExpirationDuration(15000); // Stop after 15 sec

        locationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                if (locationResult == null)
                    return;
                for (android.location.Location loc : locationResult.getLocations()) {
                    if (loc != null) {
                        // Got a location!
                        fused.removeLocationUpdates(locationCallback); // Stop updating
                        processLocation(loc, fieldKey);
                        return;
                    }
                }
            }
        };

        fused.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper());
    }

    private void processLocation(android.location.Location loc, String fieldKey) {
        if (loc == null) {
            LoadingDialog.hideLoading();
            toast("Unable to get current location. Please try again.");
            return;
        }

        detectedLatitude = loc.getLatitude();
        detectedLongitude = loc.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geo = new Geocoder(this, Locale.getDefault());
                java.util.List<Address> res = geo.getFromLocation(
                        loc.getLatitude(), loc.getLongitude(), 1);

                runOnUiThread(() -> {
                    LoadingDialog.hideLoading();
                    if (res != null && !res.isEmpty()) {
                        Address addr = res.get(0);

                        String locality = addr.getLocality(); // City (e.g. "Ahmedabad")
                        String subLocality = addr.getSubLocality(); // Area (e.g. "Gota", "SG Highway")
                        String adminArea = addr.getAdminArea(); // State
                        String subAdminArea = addr.getSubAdminArea(); // District

                        // Use getAddressLine(0) for the most detailed address
                        // This often includes nearby landmarks, road names, etc.
                        String detailedAddress = addr.getAddressLine(0);

                        // Update UI
                        if (fieldKey != null) {
                            // Dynamic field (e.g. inside form)
                            String val = (detailedAddress != null && !detailedAddress.isEmpty())
                                    ? detailedAddress
                                    : loc.getLatitude() + "," + loc.getLongitude();
                            adapter.setTextAnswer(fieldKey, val);
                            toast("Location captured");
                        } else {
                            // Static fields (Listing location)
                            detectedState = adminArea;
                            detectedDistrict = subAdminArea;

                            // Village/City: Show the most local area + city
                            // Priority: subLocality > thoroughfare > featureName > first part of
                            // addressLine
                            String areaName = subLocality; // e.g. "Gota", "SG Highway"

                            if (areaName == null || areaName.isEmpty()) {
                                // Try thoroughfare (street/road name)
                                areaName = addr.getThoroughfare();
                            }
                            if (areaName == null || areaName.isEmpty()) {
                                // Try featureName (building, POI, landmark)
                                String feature = addr.getFeatureName();
                                // Avoid using pure numbers (house numbers) as area name
                                if (feature != null && !feature.isEmpty() && !feature.matches("\\d+")) {
                                    areaName = feature;
                                }
                            }
                            if ((areaName == null || areaName.isEmpty()) && detailedAddress != null) {
                                // Extract first part before the city name from the full address line
                                // e.g. "Cluster_khodiyar 1 Sardhar Dham, Cluster_khodiyar 1, Ahmedabad..."
                                // → take "Cluster_khodiyar 1 Sardhar Dham"
                                String[] parts = detailedAddress.split(",");
                                if (parts.length >= 2 && locality != null) {
                                    // Use first segment that is NOT the city, state, or country
                                    for (String part : parts) {
                                        String trimmed = part.trim();
                                        if (!trimmed.isEmpty()
                                                && !trimmed.equalsIgnoreCase(locality)
                                                && !trimmed.equalsIgnoreCase(adminArea)
                                                && !trimmed.equals("India")
                                                && !trimmed.matches(".*\\d{6}.*")) { // skip pincode segments
                                            areaName = trimmed;
                                            break;
                                        }
                                    }
                                }
                            }

                            // Build final Village/City string: "Area, City"
                            StringBuilder villageCityBuilder = new StringBuilder();
                            if (areaName != null && !areaName.isEmpty()) {
                                villageCityBuilder.append(areaName);
                            }
                            if (locality != null && !locality.isEmpty()) {
                                // Don't duplicate if area IS the city
                                if (!locality.equalsIgnoreCase(areaName)) {
                                    if (villageCityBuilder.length() > 0)
                                        villageCityBuilder.append(", ");
                                    villageCityBuilder.append(locality);
                                }
                            }
                            String cityVal = villageCityBuilder.toString();
                            if (cityVal.isEmpty() && locality != null) {
                                cityVal = locality;
                            }

                            if (!cityVal.isEmpty())
                                etVillageCity.setText(cityVal);

                            // Address: Use the detailed address line with landmarks
                            if (detailedAddress != null && !detailedAddress.isEmpty()) {
                                etAddress.setText(detailedAddress);
                            }

                            toast("Location detected: " + (cityVal.isEmpty() ? "Coordinates set" : cityVal));
                        }
                    } else {
                        // Fallback if geocoding returns empty
                        if (fieldKey != null) {
                            adapter.setTextAnswer(fieldKey, loc.getLatitude() + "," + loc.getLongitude());
                        }
                        toast("Location coordinates captured (Address not found)");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Geocoder failed", e);
                runOnUiThread(() -> {
                    // Fallback on error
                    if (fieldKey != null) {
                        adapter.setTextAnswer(fieldKey, loc.getLatitude() + "," + loc.getLongitude());
                    }
                    toast("Location captured (Geocoding failed)");
                    LoadingDialog.hideLoading();
                });
            }
        }).start();
    }

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
                        (float) maxSize / height);
                int newW = Math.round(width * scale);
                int newH = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);

            // Iteratively reduce quality if still > 500KB
            int quality = 75;
            while (baos.toByteArray().length > 500 * 1024 && quality > 40) {
                baos.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }

            byte[] bytes = baos.toByteArray();
            Log.d(TAG, "Image compressed: " + bytes.length / 1024 + "KB at quality " + quality);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
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
        } catch (Exception ignored) {
        }

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
        } catch (Exception ignored) {
        }

        try {
            Button btn = findViewById(R.id.btnSubmit);
            if (btn != null) {
                btn.setBackground(
                        ContextCompat.getDrawable(this, R.drawable.bg_btn_primary));
                btn.setTextColor(
                        ContextCompat.getColor(this, android.R.color.white));
            }
        } catch (Exception ignored) {
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void setupLocationSettings() {
        settingsClient = LocationServices.getSettingsClient(this);
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(10000);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        builder.setAlwaysShow(true); // Important: show 'Turn On' dialog even if settings are sufficient
        locationSettingsRequest = builder.build();
    }

    private void checkLocationSettingsAndDetect(String fieldKey) {
        pendingLocationFieldKey = fieldKey;
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(locationSettingsRequest);
        task.addOnSuccessListener(this, response -> {
            // GPS is ALREADY ON -> Proceed to detect
            fetchLocationAndProcess(fieldKey);
        });

        task.addOnFailureListener(this, e -> {
            // GPS is OFF -> Show Popup
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    IntentSenderRequest isr = new IntentSenderRequest.Builder(resolvable.getResolution()).build();
                    gpsResolutionLauncher.launch(isr);
                } catch (Exception sendEx) {
                    toast("Please turn on GPS manually");
                }
            } else {
                toast("Please turn on GPS manually");
            }
        });
    }
}
