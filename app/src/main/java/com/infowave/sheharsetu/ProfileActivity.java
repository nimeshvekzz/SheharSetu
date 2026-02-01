package com.infowave.sheharsetu;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;
import com.infowave.sheharsetu.utils.LoadingDialog;

import android.widget.ImageButton;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
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

    // Session
    private SessionManager session;

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

        Log.d(TAG, "========== onCreate START ==========");

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
        Log.d(TAG, "Session initialized. Is logged in: " + session.isLoggedIn());

        bindViews();
        setupToolbar();
        setupEditFab();

        // Fetch user profile from API
        fetchUserProfile();

        Log.d(TAG, "========== onCreate END ==========");
    }

    private void bindViews() {
        Log.d(TAG, "bindViews() called");

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

        Log.d(TAG, "Views bound successfully");
    }

    private void setupToolbar() {
        tvToolbarTitle.setText("Profile");
        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void setupEditFab() {
        btnEditToggle.setOnClickListener(v -> showEditProfileBottomSheet());
    }

    /**
     * Fetch user profile from API
     */
    private void fetchUserProfile() {
        Log.d(TAG, "========== FETCH USER PROFILE START ==========");

        String accessToken = session.getAccessToken();
        Log.d(TAG, "Access Token: " + (accessToken != null ? "EXISTS (length=" + accessToken.length() + ")" : "NULL"));

        if (TextUtils.isEmpty(accessToken)) {
            Log.w(TAG, "❌ No access token - showing placeholder data");
            showPlaceholderData();
            return;
        }

        String url = ApiRoutes.GET_USER_PROFILE;
        Log.d(TAG, "API URL: " + url);

        // Show loading dialog
        LoadingDialog.showLoading(this, "Loading profile...");

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    Log.d(TAG, "✅ API Response received");
                    Log.d(TAG, "Response: " + response.toString());
                    LoadingDialog.hideLoading();

                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            updateUIWithUserData(user);
                            Log.d(TAG, "✅ Profile data loaded successfully");
                        } else {
                            String error = response.optString("error", "Unknown error");
                            Log.w(TAG, "❌ API returned success=false. Error: " + error);
                            showError("Failed to load profile: " + error);
                            showPlaceholderData();
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "❌ JSON parsing error: " + e.getMessage());
                        e.printStackTrace();
                        showError("Error parsing profile data");
                        showPlaceholderData();
                    }
                    Log.d(TAG, "========== FETCH USER PROFILE COMPLETE ==========");
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
                Log.d(TAG, "Request Headers: Authorization=Bearer [TOKEN], Content-Type=application/json");
                return headers;
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(
                10000, // 10 second timeout
                1, // 1 retry
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Log.d(TAG, "Adding request to Volley queue...");
        VolleySingleton.getInstance(this).add(req);
    }

    /**
     * Update UI with user data from API response
     */
    private void updateUIWithUserData(JSONObject user) throws JSONException {
        Log.d(TAG, "Updating UI with user data: " + user.toString());

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

        Log.d(TAG, "✅ UI updated with user data");
    }

    /**
     * Show placeholder data when API fails or no token
     */
    private void showPlaceholderData() {
        Log.d(TAG, "Showing placeholder data");
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
        Log.d(TAG, "Opening edit profile bottom sheet");

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
                Log.d(TAG, "Save button clicked");

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
        Log.d(TAG, "========== UPDATE USER PROFILE START ==========");

        String accessToken = session.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            Log.w(TAG, "❌ No access token for update");
            showError("Not logged in. Please login again.");
            return;
        }

        String url = ApiRoutes.UPDATE_USER_PROFILE;
        Log.d(TAG, "API URL: " + url);

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
            Log.d(TAG, "Request body: " + body.toString());
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
                    Log.d(TAG, "✅ Update API Response: " + response.toString());
                    btnSave.setEnabled(true);
                    btnSave.setText("Save changes");

                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            updateUIWithUserData(user);
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            Log.d(TAG, "✅ Profile updated successfully");
                        } else {
                            String error = response.optString("error", "Update failed");
                            Log.w(TAG, "❌ Update failed: " + error);
                            showError(error);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "❌ Error parsing update response: " + e.getMessage());
                        showError("Error updating profile");
                    }
                    Log.d(TAG, "========== UPDATE USER PROFILE COMPLETE ==========");
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

        Log.d(TAG, "Adding update request to Volley queue...");
        VolleySingleton.getInstance(this).add(req);
    }
}
