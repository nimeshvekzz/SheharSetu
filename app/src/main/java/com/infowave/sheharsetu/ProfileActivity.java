package com.infowave.sheharsetu;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.infowave.sheharsetu.Adapter.LanguageAdapter;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.Adapter.MyListingsAdapter;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

import android.widget.ImageButton;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    // View mode widgets (main screen)
    private TextView tvAvatarLetter, tvFullName, tvPhone, tvPlaceTypeChip;
    private TextView tvSurnameValue, tvContactPhone;
    private TextView tvAddressLine, tvVillage, tvDistrict, tvState, tvPincode;

    private ImageButton btnBack;
    private TextView tvToolbarTitle;
    private ProgressBar progressBar;
    private View rootProfile;

    // Bottom-right FAB for edit
    private FloatingActionButton btnEditToggle;

    // My Listings section
    private RecyclerView rvMyListings;
    private CardView cardEmptyListings;
    private MaterialButton btnPostFirstListing;
    private MyListingsAdapter myListingsAdapter;

    // Session
    private SessionManager session;

    // Language Settings
    private View layoutLanguage;
    private TextView tvLanguageValue;

    // Cached user data for edit dialog
    private String cachedFullName = "";
    private String cachedSurname = "";
    private String cachedPhone = "";
    private String cachedAddress = "";
    private String cachedVillage = "";
    private String cachedDistrict = "";
    private String cachedState = "";
    private String cachedPincode = "";
    private String cachedPlaceType = "village";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Black status + navigation bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        setContentView(R.layout.activity_profile);

        // Initialize session
        session = new SessionManager(this);
        bindViews();
        setupToolbar();
        setupEditFab();
        setupLanguageSettings();
        setupMyListings();

        // Fetch user profile from API
        fetchUserProfile();

        // Fetch user listings
        fetchMyListings();
    }

    private void bindViews() {
        rootProfile = findViewById(R.id.rootProfile);
        btnBack = findViewById(R.id.btnBack);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);

        tvAvatarLetter = findViewById(R.id.tvAvatarLetter);
        tvFullName = findViewById(R.id.tvFullName);
        tvPhone = findViewById(R.id.tvPhone);
        tvPlaceTypeChip = findViewById(R.id.tvPlaceTypeChip);
        tvSurnameValue = findViewById(R.id.tvSurnameValue);
        tvContactPhone = findViewById(R.id.tvContactPhone);
        tvAddressLine = findViewById(R.id.tvAddressLine);
        tvVillage = findViewById(R.id.tvVillage);
        tvDistrict = findViewById(R.id.tvDistrict);
        tvState = findViewById(R.id.tvState);
        tvPincode = findViewById(R.id.tvPincode);

        // FAB (bottom-right)
        btnEditToggle = findViewById(R.id.btnEditToggle);

        // Language Settings
        layoutLanguage = findViewById(R.id.layoutLanguage);
        tvLanguageValue = findViewById(R.id.tvLanguageValue);

        // My Listings section
        rvMyListings = findViewById(R.id.rvMyListings);
        cardEmptyListings = findViewById(R.id.cardEmptyListings);
        btnPostFirstListing = findViewById(R.id.btnPostFirstListing);
    }

    private void setupToolbar() {
        tvToolbarTitle.setText("Profile");
        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void setupEditFab() {
        btnEditToggle.setOnClickListener(v -> showEditProfileBottomSheet());
    }

    /**
     * Setup language settings row
     */
    private void setupLanguageSettings() {
        // Show current language
        if (tvLanguageValue != null) {
            tvLanguageValue.setText(session.getLangName());
        }

        // Click listener for language row
        if (layoutLanguage != null) {
            layoutLanguage.setOnClickListener(v -> showLanguageBottomSheet());
        }
    }

    /**
     * Show language picker bottom sheet
     */
    private void showLanguageBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this,
                com.google.android.material.R.style.ThemeOverlay_MaterialComponents_BottomSheetDialog);

        View view = LayoutInflater.from(this).inflate(R.layout.sheet_language_picker, null, false);
        dialog.setContentView(view);

        RecyclerView rvLangs = view.findViewById(R.id.rvLanguages);
        View progress = view.findViewById(R.id.progressLanguages);

        // Language list
        java.util.List<String[]> languages = new java.util.ArrayList<>();

        // Adapter with selection callback
        LanguageAdapter adapter = new LanguageAdapter(languages, lang -> {
            // lang[0] = code, lang[1] = native_name
            session.setLang(lang[0], lang[1]);

            // Save to SharedPreferences (same key as LanguageSelection)
            getSharedPreferences("sheharsetu_prefs", MODE_PRIVATE).edit()
                    .putString("app_lang_code", lang[0])
                    .putString("app_lang_name", lang[1])
                    .apply();

            // Apply language
            LanguageManager.apply(this, lang[0]);

            dialog.dismiss();

            // Restart app for full effect
            Toast.makeText(this, "Language changed to " + lang[1], Toast.LENGTH_SHORT).show();
            restartApp();
        });

        rvLangs.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        rvLangs.setAdapter(adapter);

        // Fetch languages from API
        fetchLanguagesForPicker(languages, adapter, progress, rvLangs);

        dialog.show();
    }

    /**
     * Fetch languages from API for picker
     */
    private void fetchLanguagesForPicker(java.util.List<String[]> languages,
            LanguageAdapter adapter,
            View progress,
            RecyclerView rvLangs) {
        progress.setVisibility(View.VISIBLE);
        rvLangs.setVisibility(View.INVISIBLE);

        String url = ApiRoutes.GET_LANGUAGES;

        com.android.volley.toolbox.StringRequest req = new com.android.volley.toolbox.StringRequest(
                Request.Method.GET,
                url,
                response -> {
                    progress.setVisibility(View.GONE);
                    rvLangs.setVisibility(View.VISIBLE);

                    try {
                        org.json.JSONObject resp = new org.json.JSONObject(response.trim());
                        if (!resp.optBoolean("ok", false))
                            return;

                        org.json.JSONArray arr = resp.optJSONArray("data");
                        if (arr == null)
                            return;

                        languages.clear();
                        int englishIndex = -1;

                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject o = arr.optJSONObject(i);
                            if (o == null)
                                continue;
                            if (o.optInt("enabled", 1) != 1)
                                continue;

                            String code = o.optString("code", "").trim();
                            String nativeName = o.optString("native_name", "").trim();
                            String englishName = o.optString("english_name", "").trim();

                            if (code.isEmpty() || nativeName.isEmpty())
                                continue;

                            languages.add(new String[] { code, nativeName, englishName });

                            if ("en".equalsIgnoreCase(code)) {
                                englishIndex = languages.size() - 1;
                            }
                        }

                        // Move English to top
                        if (englishIndex > 0) {
                            String[] en = languages.remove(englishIndex);
                            languages.add(0, en);
                        }

                        adapter.notifyDataSetChanged();

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing languages: " + e.getMessage());
                    }
                },
                error -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load languages", Toast.LENGTH_SHORT).show();
                });

        req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }

    /**
     * Restart app to apply language change fully
     */
    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finishAffinity();
    }

    /**
     * Fetch user profile from API
     */
    private void fetchUserProfile() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            showPlaceholderData();
            return;
        }

        String url = ApiRoutes.GET_USER_PROFILE;
        // Show loading dialog
        LoadingDialog.showLoading(this, "Loading profile...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    LoadingDialog.hideLoading();

                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            updateUIWithUserData(user);
                        } else {
                            String error = response.optString("error", "Unknown error");
                            showError("Failed to load profile: " + error);
                            showPlaceholderData();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "❌ JSON parsing error: " + e.getMessage());
                        e.printStackTrace();
                        showError("Error parsing profile data");
                        showPlaceholderData();
                    }
                },
                error -> {
                    Log.e(TAG, "❌ ========== USER PROFILE API ERROR ==========");
                    Log.e(TAG, "Error: " + error.toString());
                    LoadingDialog.hideLoading();

                    if (error.networkResponse != null) {
                        Log.e(TAG, "Status Code: " + error.networkResponse.statusCode);
                        Log.e(TAG, "Response Data: " + new String(error.networkResponse.data));
                    } else {
                        Log.e(TAG, "Network Response: NULL (likely network/SSL error)");
                    }

                    showError("Failed to load profile. Check your connection.");
                    showPlaceholderData();
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

        req.setRetryPolicy(new DefaultRetryPolicy(
                10000, // 10 second timeout
                1, // 1 retry
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    /**
     * Update UI with user data from API response
     */
    private void updateUIWithUserData(JSONObject user) throws JSONException {
        // Cache data for edit dialog
        cachedFullName = user.optString("full_name", "");
        cachedSurname = user.optString("surname", "");
        cachedPhone = user.optString("phone", "");
        cachedAddress = user.optString("address", "");
        cachedVillage = user.optString("village_name", "");
        cachedDistrict = user.optString("district", "");
        cachedState = user.optString("state", "");
        cachedPincode = user.optString("pincode", "");
        cachedPlaceType = user.optString("place_type", "village");

        String formattedPhone = user.optString("formatted_phone", "");
        String displayName = user.optString("name", cachedFullName);

        // Update view mode TextViews
        tvFullName.setText(displayName);
        tvPhone.setText(formattedPhone);
        tvContactPhone.setText(formattedPhone);
        tvSurnameValue.setText(cachedSurname.isEmpty() ? "-" : cachedSurname);
        tvAddressLine.setText(cachedAddress.isEmpty() ? "-" : cachedAddress);
        tvVillage.setText(cachedVillage.isEmpty() ? "-" : cachedVillage);
        tvDistrict.setText(cachedDistrict.isEmpty() ? "-" : cachedDistrict);
        tvState.setText(cachedState.isEmpty() ? "-" : cachedState);
        tvPincode.setText(cachedPincode.isEmpty() ? "-" : cachedPincode);

        // Place type chip - capitalize first letter and show it
        String displayPlaceType = cachedPlaceType.isEmpty() ? "Village"
                : cachedPlaceType.substring(0, 1).toUpperCase() + cachedPlaceType.substring(1);
        tvPlaceTypeChip.setText(displayPlaceType);
        tvPlaceTypeChip.setVisibility(View.VISIBLE);

        // Avatar = first letter of full name
        if (!displayName.isEmpty()) {
            char first = Character.toUpperCase(displayName.trim().charAt(0));
            tvAvatarLetter.setText(String.valueOf(first));
        }
    }

    /**
     * Show placeholder data when API fails or no token
     */
    @SuppressLint("SetTextI18n")
    private void showPlaceholderData() {
        tvFullName.setText("Guest User");
        tvPhone.setText("-");
        tvContactPhone.setText("-");
        tvSurnameValue.setText("-");
        tvAddressLine.setText("-");
        tvVillage.setText("-");
        tvDistrict.setText("-");
        tvState.setText("-");
        tvPincode.setText("-");
        tvPlaceTypeChip.setText("Village");
        tvAvatarLetter.setText("G");
    }

    /**
     * Show/hide loading state
     */
    private void showLoading(boolean show) {
        // Disable FAB while loading
        btnEditToggle.setEnabled(!show);
        btnEditToggle.setAlpha(show ? 0.5f : 1.0f);
    }

    /**
     * Show error message to user
     */
    private void showError(String message) {
        if (rootProfile != null) {
            Snackbar.make(rootProfile, message, Snackbar.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Professional bottom sheet for editing profile.
     */
    private void showEditProfileBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(
                this,
                com.google.android.material.R.style.ThemeOverlay_MaterialComponents_BottomSheetDialog);

        View view = LayoutInflater.from(this)
                .inflate(R.layout.layout_profile_edit_bottom_sheet, null, false);

        dialog.setContentView(view);

        // --- Bottom sheet fields ---
        TextInputEditText etFullNameBottom = view.findViewById(R.id.etFullNameBottom);
        TextInputEditText etSurnameBottom = view.findViewById(R.id.etSurnameBottom);
        TextInputEditText etPhoneBottom = view.findViewById(R.id.etPhoneBottom);
        AutoCompleteTextView etPlaceTypeBottom = view.findViewById(R.id.etPlaceTypeBottom);
        TextInputEditText etAddressBottom = view.findViewById(R.id.etAddressBottom);
        TextInputEditText etVillageBottom = view.findViewById(R.id.etVillageBottom);
        TextInputEditText etDistrictBottom = view.findViewById(R.id.etDistrictBottom);
        TextInputEditText etStateBottom = view.findViewById(R.id.etStateBottom);
        TextInputEditText etPincodeBottom = view.findViewById(R.id.etPincodeBottom);

        MaterialButton btnCancelBottom = view.findViewById(R.id.btnCancelBottom);
        MaterialButton btnSaveProfileBottom = view.findViewById(R.id.btnSaveProfileBottom);

        // --- Prefill with cached API data ---
        if (etFullNameBottom != null)
            etFullNameBottom.setText(cachedFullName);
        if (etSurnameBottom != null)
            etSurnameBottom.setText(cachedSurname);
        if (etPhoneBottom != null) {
            etPhoneBottom.setText(cachedPhone);
            etPhoneBottom.setEnabled(false); // Phone is read-only (login ID)
            etPhoneBottom.setAlpha(0.6f);
        }
        if (etAddressBottom != null)
            etAddressBottom.setText(cachedAddress);
        if (etVillageBottom != null)
            etVillageBottom.setText(cachedVillage);
        if (etDistrictBottom != null)
            etDistrictBottom.setText(cachedDistrict);
        if (etStateBottom != null)
            etStateBottom.setText(cachedState);
        if (etPincodeBottom != null)
            etPincodeBottom.setText(cachedPincode);

        // Place type dropdown
        if (etPlaceTypeBottom != null) {
            String[] placeTypes = new String[] { "Village", "City" };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    placeTypes);
            etPlaceTypeBottom.setAdapter(adapter);
            // Set current value with proper capitalization
            String displayPlaceType = cachedPlaceType.isEmpty() ? "Village"
                    : cachedPlaceType.substring(0, 1).toUpperCase() + cachedPlaceType.substring(1);
            etPlaceTypeBottom.setText(displayPlaceType, false);
        }

        // Cancel button
        if (btnCancelBottom != null) {
            btnCancelBottom.setOnClickListener(v -> dialog.dismiss());
        }

        // Save button
        if (btnSaveProfileBottom != null) {
            btnSaveProfileBottom.setOnClickListener(v -> {
                String newFullName = getTextFromEditText(etFullNameBottom);
                String newSurname = getTextFromEditText(etSurnameBottom);
                String newPlaceType = getTextFromEditText(etPlaceTypeBottom);
                String newAddress = getTextFromEditText(etAddressBottom);
                String newVillage = getTextFromEditText(etVillageBottom);
                String newDistrict = getTextFromEditText(etDistrictBottom);
                String newState = getTextFromEditText(etStateBottom);
                String newPincode = getTextFromEditText(etPincodeBottom);

                // Validation
                if (newFullName.isEmpty()) {
                    if (etFullNameBottom != null)
                        etFullNameBottom.setError("Enter full name");
                    return;
                }
                if (!newPincode.isEmpty() && newPincode.length() != 6) {
                    if (etPincodeBottom != null)
                        etPincodeBottom.setError("Pincode must be 6 digits");
                    return;
                }

                // Convert place type to lowercase for API
                String placeTypeApi = newPlaceType.toLowerCase();

                // Call update API
                updateUserProfile(
                        newFullName, newSurname, newAddress, newVillage,
                        newDistrict, placeTypeApi, newState, newPincode,
                        dialog, btnSaveProfileBottom);
            });
        }

        dialog.show();
    }

    /**
     * Helper to get text from EditText safely
     */
    private String getTextFromEditText(TextView et) {
        if (et != null && et.getText() != null) {
            return et.getText().toString().trim();
        }
        return "";
    }

    /**
     * Update user profile via API
     */
    private void updateUserProfile(
            String fullName, String surname, String address, String villageName,
            String district, String placeType, String state, String pincode,
            BottomSheetDialog dialog, MaterialButton btnSave) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            showError("Not logged in. Please login again.");
            return;
        }

        String url = ApiRoutes.UPDATE_USER_PROFILE;
        // Build request body
        JSONObject body = new JSONObject();
        try {
            body.put("full_name", fullName);
            body.put("surname", surname);
            body.put("address", address);
            body.put("village_name", villageName);
            body.put("district", district);
            body.put("place_type", placeType);
            body.put("state", state);
            body.put("pincode", pincode);
        } catch (JSONException e) {
            Log.e(TAG, "Error building request body: " + e.getMessage());
            showError("Error preparing update request");
            return;
        }

        // Disable save button while updating
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save changes");

                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            updateUIWithUserData(user);
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String error = response.optString("error", "Update failed");
                            showError(error);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "❌ Error parsing update response: " + e.getMessage());
                        showError("Error updating profile");
                    }
                },
                error -> {
                    Log.e(TAG, "❌ ========== UPDATE API ERROR ==========");
                    Log.e(TAG, "Error: " + error.toString());
                    btnSave.setEnabled(true);
                    btnSave.setText("Save changes");

                    String errorMsg = "Failed to update profile";
                    if (error.networkResponse != null) {
                        Log.e(TAG, "Status Code: " + error.networkResponse.statusCode);
                        try {
                            String responseData = new String(error.networkResponse.data);
                            Log.e(TAG, "Response Data: " + responseData);
                            JSONObject errorJson = new JSONObject(responseData);
                            errorMsg = errorJson.optString("error", errorMsg);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response");
                        }
                    }
                    showError(errorMsg);
                    Log.e(TAG, "========== UPDATE API ERROR END ==========");
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                15000, // 15 second timeout for update
                0, // No retries
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }

    // ==================== MY LISTINGS SECTION ====================

    private void setupMyListings() {
        // Setup RecyclerView with horizontal layout
        rvMyListings.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        myListingsAdapter = new MyListingsAdapter(this);
        rvMyListings.setAdapter(myListingsAdapter);

        myListingsAdapter.setOnListingActionListener(new MyListingsAdapter.OnListingActionListener() {
            @Override
            public void onListingClick(int listingId) {
                // Open ProductDetail
                Intent intent = new Intent(ProfileActivity.this, ProductDetail.class);
                intent.putExtra("listing_id", listingId);
                startActivity(intent);
            }

            @Override
            public void onMarkSoldClick(int listingId, boolean currentlySold) {
                showMarkSoldConfirmation(listingId, currentlySold);
            }
        });

        // Post first listing button click
        btnPostFirstListing.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, CategorySelectActivity.class);
            startActivity(intent);
        });
    }

    private void fetchMyListings() {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            showEmptyListingsState();
            return;
        }

        // Show custom loading dialog
        LoadingDialog.showLoading(this, "Loading your listings...");
        rvMyListings.setVisibility(View.GONE);
        cardEmptyListings.setVisibility(View.GONE);

        StringRequest req = new StringRequest(
                Request.Method.GET,
                ApiRoutes.GET_USER_LISTINGS,
                response -> {
                    LoadingDialog.hideLoading();

                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONArray listingsArray = json.optJSONArray("listings");
                            if (listingsArray != null && listingsArray.length() > 0) {
                                List<MyListingsAdapter.ListingItem> items = new ArrayList<>();
                                for (int i = 0; i < listingsArray.length(); i++) {
                                    items.add(MyListingsAdapter.ListingItem.fromJson(listingsArray.getJSONObject(i)));
                                }
                                myListingsAdapter.setItems(items);
                                rvMyListings.setVisibility(View.VISIBLE);
                                cardEmptyListings.setVisibility(View.GONE);
                            } else {
                                showEmptyListingsState();
                            }
                        } else {
                            String error = json.optString("error", "Failed to load listings");
                            showEmptyListingsState();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parse error: " + e.getMessage());
                        showEmptyListingsState();
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Listings API error: " + error.toString());
                    LoadingDialog.hideLoading();
                    showEmptyListingsState();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        req.setShouldCache(false); // Disable Volley cache to always get fresh data
        VolleySingleton.getInstance(this).add(req);
    }

    private void showEmptyListingsState() {
        rvMyListings.setVisibility(View.GONE);
        cardEmptyListings.setVisibility(View.VISIBLE);
    }

    private void showMarkSoldConfirmation(int listingId, boolean currentlySold) {
        String title = currentlySold ? "Mark as Available" : "Mark as Sold";
        String message = currentlySold
                ? "This will make your listing visible to buyers again."
                : "This will mark your listing as sold. Buyers will see it's no longer available.";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Confirm", (dialog, which) -> markListingAsSold(listingId, !currentlySold))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markListingAsSold(int listingId, boolean markAsSold) {
        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            return;
        }

        LoadingDialog.showLoading(this, markAsSold ? "Marking as sold..." : "Marking as available...");

        StringRequest req = new StringRequest(
                Request.Method.POST,
                ApiRoutes.MARK_LISTING_SOLD,
                response -> {
                    LoadingDialog.hideLoading();

                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            // Update adapter UI
                            myListingsAdapter.updateItemSoldStatus(listingId, markAsSold);
                            String msg = markAsSold ? "Marked as sold!" : "Marked as available!";
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        } else {
                            String error = json.optString("error", "Failed to update");
                            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    Log.e(TAG, "❌ Mark sold error: " + error.toString());
                    LoadingDialog.hideLoading();
                    Toast.makeText(this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                return headers;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("listing_id", String.valueOf(listingId));
                params.put("is_sold", markAsSold ? "1" : "0");
                return params;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance(this).add(req);
    }
}
