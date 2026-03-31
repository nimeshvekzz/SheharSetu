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
import com.google.android.material.textfield.TextInputLayout;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageAdapter;
import com.infowave.sheharsetu.Adapter.LanguageManager;

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

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        // Apply edged-to-edge window decor to draw behind system bars (like MainActivity)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Force status bar and navigation bar icons to be light (white)
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false); // false = white icons
            windowInsetsController.setAppearanceLightNavigationBars(false); // false = white icons
        }

        setContentView(R.layout.activity_profile);

        // Apply dynamically heights to the custom background views
        View viewStatusBarBg = findViewById(R.id.viewStatusBarBg);
        View viewNavBarBg = findViewById(R.id.viewNavBarBg);

        View rootProfile = findViewById(R.id.rootProfile);
        if (rootProfile != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootProfile, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());

                if (viewStatusBarBg != null) {
                    viewStatusBarBg.getLayoutParams().height = systemBars.top;
                    viewStatusBarBg.requestLayout();
                }

                if (viewNavBarBg != null) {
                    viewNavBarBg.getLayoutParams().height = systemBars.bottom;
                    viewNavBarBg.requestLayout();
                }

                return insets;
            });
        }

        // Initialize session
        session = new SessionManager(this);
        bindViews();
        setupToolbar();
        setupEditFab();
        setupLanguageSettings();
        prefetchAndApplyStaticTexts();
        // Fetch user profile from API
        fetchUserProfile();
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

    }


    private boolean shouldSkipDynamicText(View view) {
        if (view == null) return false;
        int id = view.getId();
        return id == R.id.tvFullName
                || id == R.id.tvPhone
                || id == R.id.tvPlaceTypeChip
                || id == R.id.tvSurnameValue
                || id == R.id.tvContactPhone
                || id == R.id.tvAddressLine
                || id == R.id.tvVillage
                || id == R.id.tvDistrict
                || id == R.id.tvState
                || id == R.id.tvPincode
                || id == R.id.tvLanguageValue
                || id == R.id.tvAvatarLetter;
    }

    private void collectTranslatableTexts(View view, List<String> out) {
        if (view == null) return;

        if (view instanceof TextInputLayout) {
            CharSequence hint = ((TextInputLayout) view).getHint();
            if (!TextUtils.isEmpty(hint)) out.add(hint.toString());
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (!(tv instanceof TextInputEditText) && !(tv instanceof AutoCompleteTextView) && !shouldSkipDynamicText(tv)) {
                CharSequence text = tv.getText();
                if (!TextUtils.isEmpty(text)) out.add(text.toString());
            }
            CharSequence hint = tv.getHint();
            if (!TextUtils.isEmpty(hint)) out.add(hint.toString());
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                collectTranslatableTexts(vg.getChildAt(i), out);
            }
        }
    }

    private void applyTranslationsToViewTree(View view) {
        if (view == null) return;

        if (view instanceof TextInputLayout) {
            TextInputLayout til = (TextInputLayout) view;
            CharSequence hint = til.getHint();
            if (!TextUtils.isEmpty(hint)) {
                til.setHint(I18n.t(this, hint.toString()));
            }
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (!(tv instanceof TextInputEditText) && !(tv instanceof AutoCompleteTextView) && !shouldSkipDynamicText(tv)) {
                CharSequence text = tv.getText();
                if (!TextUtils.isEmpty(text)) {
                    tv.setText(I18n.t(this, text.toString()));
                }
            }
            CharSequence hint = tv.getHint();
            if (!TextUtils.isEmpty(hint)) {
                tv.setHint(I18n.t(this, hint.toString()));
            }
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTranslationsToViewTree(vg.getChildAt(i));
            }
        }
    }

    private void prefetchAndTranslateViewTree(View view) {
        if (view == null) return;
        List<String> keys = new ArrayList<>();
        collectTranslatableTexts(view, keys);
        I18n.prefetch(this, keys, () -> applyTranslationsToViewTree(view), () -> applyTranslationsToViewTree(view));
    }

    private void prefetchAndApplyStaticTexts() {
        if (rootProfile == null) return;
        List<String> keys = new ArrayList<>();
        keys.add("Profile");
        keys.add("Loading profile...");
        keys.add("Failed to load languages");
        keys.add("Language changed to");
        keys.add("Guest User");
        keys.add("Village");
        keys.add("City");
        keys.add("Enter full name");
        keys.add("Pincode must be 6 digits");
        keys.add("Save changes");
        keys.add("Saving...");
        collectTranslatableTexts(rootProfile, keys);

        I18n.prefetch(this, keys, () -> {
            if (tvToolbarTitle != null) {
                tvToolbarTitle.setText(I18n.t(this, "Profile"));
            }
            applyTranslationsToViewTree(rootProfile);
        }, () -> {
            if (tvToolbarTitle != null) {
                tvToolbarTitle.setText(I18n.t(this, "Profile"));
            }
            applyTranslationsToViewTree(rootProfile);
        });
    }

    private void renderTranslatedProfileData(String displayName, String formattedPhone, String displayPlaceType) {
        List<String> keys = new ArrayList<>();
        if (!TextUtils.isEmpty(displayName)) keys.add(displayName);
        if (!TextUtils.isEmpty(cachedSurname)) keys.add(cachedSurname);
        if (!TextUtils.isEmpty(cachedAddress)) keys.add(cachedAddress);
        if (!TextUtils.isEmpty(cachedVillage)) keys.add(cachedVillage);
        if (!TextUtils.isEmpty(cachedDistrict)) keys.add(cachedDistrict);
        if (!TextUtils.isEmpty(cachedState)) keys.add(cachedState);
        if (!TextUtils.isEmpty(displayPlaceType)) keys.add(displayPlaceType);

        I18n.prefetch(this, keys, () -> {
            tvFullName.setText(TextUtils.isEmpty(displayName) ? "-" : I18n.t(this, displayName));
            tvPhone.setText(formattedPhone);
            tvContactPhone.setText(formattedPhone);
            tvSurnameValue.setText(cachedSurname.isEmpty() ? "-" : I18n.t(this, cachedSurname));
            tvAddressLine.setText(cachedAddress.isEmpty() ? "-" : I18n.t(this, cachedAddress));
            tvVillage.setText(cachedVillage.isEmpty() ? "-" : I18n.t(this, cachedVillage));
            tvDistrict.setText(cachedDistrict.isEmpty() ? "-" : I18n.t(this, cachedDistrict));
            tvState.setText(cachedState.isEmpty() ? "-" : I18n.t(this, cachedState));
            tvPincode.setText(cachedPincode.isEmpty() ? "-" : cachedPincode);
            tvPlaceTypeChip.setText(TextUtils.isEmpty(displayPlaceType) ? I18n.t(this, "Village") : I18n.t(this, displayPlaceType));
            tvPlaceTypeChip.setVisibility(View.VISIBLE);

            if (!TextUtils.isEmpty(displayName)) {
                char first = Character.toUpperCase(displayName.trim().charAt(0));
                tvAvatarLetter.setText(String.valueOf(first));
            }
        }, () -> {
            tvFullName.setText(TextUtils.isEmpty(displayName) ? "-" : displayName);
            tvPhone.setText(formattedPhone);
            tvContactPhone.setText(formattedPhone);
            tvSurnameValue.setText(cachedSurname.isEmpty() ? "-" : cachedSurname);
            tvAddressLine.setText(cachedAddress.isEmpty() ? "-" : cachedAddress);
            tvVillage.setText(cachedVillage.isEmpty() ? "-" : cachedVillage);
            tvDistrict.setText(cachedDistrict.isEmpty() ? "-" : cachedDistrict);
            tvState.setText(cachedState.isEmpty() ? "-" : cachedState);
            tvPincode.setText(cachedPincode.isEmpty() ? "-" : cachedPincode);
            tvPlaceTypeChip.setText(TextUtils.isEmpty(displayPlaceType) ? "Village" : displayPlaceType);
            tvPlaceTypeChip.setVisibility(View.VISIBLE);

            if (!TextUtils.isEmpty(displayName)) {
                char first = Character.toUpperCase(displayName.trim().charAt(0));
                tvAvatarLetter.setText(String.valueOf(first));
            }
        });
    }

    private void setupToolbar() {
        tvToolbarTitle.setText(I18n.t(this, "Profile"));
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
            Toast.makeText(this, I18n.t(this, "Language changed to") + " " + lang[1], Toast.LENGTH_SHORT).show();
            restartApp();
        });

        rvLangs.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        rvLangs.setAdapter(adapter);

        // Fetch languages from API
        fetchLanguagesForPicker(languages, adapter, progress, rvLangs);

        prefetchAndTranslateViewTree(view);
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
                    Toast.makeText(this, I18n.t(this, "Failed to load languages"), Toast.LENGTH_SHORT).show();
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
        LoadingDialog.showLoading(this, I18n.t(this, "Loading profile..."));

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

        // Place type chip - capitalize first letter and show it
        String displayPlaceType = cachedPlaceType.isEmpty() ? "Village"
                : cachedPlaceType.substring(0, 1).toUpperCase() + cachedPlaceType.substring(1);

        renderTranslatedProfileData(displayName, formattedPhone, displayPlaceType);
    }

    /**
     * Show placeholder data when API fails or no token
     */
    @SuppressLint("SetTextI18n")
    private void showPlaceholderData() {
        tvFullName.setText(I18n.t(this, "Guest User"));
        tvPhone.setText("-");
        tvContactPhone.setText("-");
        tvSurnameValue.setText("-");
        tvAddressLine.setText("-");
        tvVillage.setText("-");
        tvDistrict.setText("-");
        tvState.setText("-");
        tvPincode.setText("-");
        tvPlaceTypeChip.setText(I18n.t(this, "Village"));
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
            String[] placeTypes = new String[] { I18n.t(this, "Village"), I18n.t(this, "City") };
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    placeTypes);
            etPlaceTypeBottom.setAdapter(adapter);
            // Set current value with proper capitalization
            String displayPlaceType = cachedPlaceType.isEmpty() ? I18n.t(this, "Village")
                    : I18n.t(this, cachedPlaceType.substring(0, 1).toUpperCase() + cachedPlaceType.substring(1));
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
                        etFullNameBottom.setError(I18n.t(this, "Enter full name"));
                    return;
                }
                if (!newPincode.isEmpty() && newPincode.length() != 6) {
                    if (etPincodeBottom != null)
                        etPincodeBottom.setError(I18n.t(this, "Pincode must be 6 digits"));
                    return;
                }

                // Convert place type to lowercase for API
                String placeTypeApi = newPlaceType.equalsIgnoreCase(I18n.t(this, "City")) ? "city" : "village";

                // Call update API
                updateUserProfile(
                        newFullName, newSurname, newAddress, newVillage,
                        newDistrict, placeTypeApi, newState, newPincode,
                        dialog, btnSaveProfileBottom);
            });
        }

        prefetchAndTranslateViewTree(view);
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
            showError(I18n.t(this, "Not logged in. Please login again."));
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
            showError(I18n.t(this, "Error preparing update request"));
            return;
        }

        // Disable save button while updating
        btnSave.setEnabled(false);
        btnSave.setText(I18n.t(this, "Saving..."));

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                url,
                body,
                response -> {
                    btnSave.setEnabled(true);
                    btnSave.setText(I18n.t(this, "Save changes"));

                    try {
                        if (response.getBoolean("success")) {
                            JSONObject user = response.getJSONObject("user");
                            updateUIWithUserData(user);
                            Toast.makeText(this, I18n.t(this, "Profile updated successfully!"), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String error = response.optString("error", I18n.t(this, "Update failed"));
                            showError(error);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "❌ Error parsing update response: " + e.getMessage());
                        showError(I18n.t(this, "Error updating profile"));
                    }
                },
                error -> {
                    Log.e(TAG, "❌ ========== UPDATE API ERROR ==========");
                    Log.e(TAG, "Error: " + error.toString());
                    btnSave.setEnabled(true);
                    btnSave.setText(I18n.t(this, "Save changes"));

                    String errorMsg = I18n.t(this, "Failed to update profile");
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

}
