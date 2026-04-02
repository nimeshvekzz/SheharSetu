package com.anvexgroup.sheharsetu;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.anvexgroup.sheharsetu.Adapter.I18n;
import com.anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.anvexgroup.sheharsetu.core.SessionManager;
import com.anvexgroup.sheharsetu.net.ApiRoutes;
import com.anvexgroup.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * - Shows loader until all static labels/hints are translated.
 * - Fetches States/Districts, translates them, then enables the dropdowns.
 * - Status bar overlap fixed via insets.
 * - OTP/Register/Login logic untouched.
 */
public class UserInfoActivity extends AppCompatActivity {

    /* ===== Google Translate ===== */
    private static final String G_TRANSLATE_URL = "https://translation.googleapis.com/language/translate/v2";
    // Per your request (for production, proxy on server):
    private static final String GOOGLE_TRANSLATE_API_KEY = "AIzaSyCkUxQSJ1jNt0q_CcugieFl5vezsNAUxe0";

    private TextInputEditText etName, etSurname, etMobile, etPlaceName, etAddress, etPincode;
    private AutoCompleteTextView spState, spDistrict;
    private ChipGroup rgPlaceType;
    private MaterialCheckBox cbTerms;
    private MaterialButton btnContinue;
    private TextView tvLangBadge, tvLoginLink, tvTitle;

    // dynamic
    private final ArrayList<String> stateNames = new ArrayList<>();
    private final ArrayList<String> districtNames = new ArrayList<>();
    private final HashMap<String, String> stateIdByName = new HashMap<>();
    private final HashMap<String, String> districtIdByName = new HashMap<>();
    private ArrayAdapter<String> stateAdapter;
    private ArrayAdapter<String> districtAdapter;

    private BottomSheetDialog otpDialog;
    private CountDownTimer resendTimer;

    private SessionManager session;
    private boolean isSendingOtp = false;
    private boolean isSavingProfile = false;
    private boolean isVerifyingOtp = false;

    private Dialog loadingDialog;
    private Toast singleToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        setContentView(R.layout.activity_user_info);

        // Status bar: no overlap, white icons on black bar
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        applyStatusBarInsets();

        bindViews();

        // Adapters for dropdowns
        stateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, stateNames);
        districtAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, districtNames);
        spState.setAdapter(stateAdapter);
        spDistrict.setAdapter(districtAdapter);
        spDistrict.setEnabled(false);

        // ====== 1) BLOCKING LOADER until static hints/text are translated ======
        showLoading(I18n.t(this, "Loading…"));

        java.util.List<String> staticKeys = java.util.Arrays.asList(
                "Language",
                "Complete Your Profile",
                "Name", "Surname", "Mobile number",
                "Select State", "Select District",
                "Village", "City", "Village / City Name",
                "Village name", "City name",
                "Full address", "Pincode",
                "I agree to the Terms  Conditions",
                "Already have an account? Log in",
                "Continue",
                "Loading…",
                "Enter valid name",
                "Enter valid surname",
                "Enter 10-digit Indian mobile",
                "Select state",
                "Select district",
                "Enter village/city",
                "Enter full address",
                "Enter 6-digit pincode",
                "Please accept Terms & Conditions",
                "You are already registered. Please login.",
                "Failed to send OTP",
                "Invalid/expired OTP",
                "Login failed: bad response",
                "Saving profile…",
                "Profile save failed",
                "Server unavailable. Please try again.",
                "Network error",
                "OTP sent to",
                "Enter 6 digits",
                "Resend in",
                "Loading states…",
                "Loading districts…",
                "No states found",
                "No districts found",
                "Failed to load states",
                "Failed to load districts");

        // Prefetch, then apply all static UI; keep loader visible until done
        I18n.prefetch(this, staticKeys, () -> {
            // Apply static labels/hints now that translations are cached
            tvLangBadge.setText(I18n.t(this, "Language") + ": " + session.getLangName());
            I18n.translateAndApplyText(tvTitle, this);
            I18n.translateAndApplyText(tvLoginLink, this);
            btnContinue.setText(I18n.t(this, btnContinue.getText().toString()));

            I18n.translateAndApplyHint((TextInputLayout) etName.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) etSurname.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) etMobile.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) spState.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) spDistrict.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) etPlaceName.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) etAddress.getParent().getParent(), this);
            I18n.translateAndApplyHint((TextInputLayout) etPincode.getParent().getParent(), this);

            Chip rbVillage = findViewById(R.id.rbVillage);
            Chip rbCity = findViewById(R.id.rbCity);
            if (rbVillage != null)
                rbVillage.setText(I18n.t(this, rbVillage.getText().toString()));
            if (rbCity != null)
                rbCity.setText(I18n.t(this, rbCity.getText().toString()));

            // ====== 2) Now fetch & translate States with loader kept ON ======
            preloadStateUi();
            fetchStates();
        }, () -> {
            // Even if prefetch fails, proceed (fallbacks will be English)
            tvLangBadge.setText(I18n.t(this, "Language") + ": " + session.getLangName());
            preloadStateUi();
            fetchStates();
        });

        spState.setOnItemClickListener((p, v, pos, id) -> {
            String pickedDisplay = stateAdapter.getItem(pos);
            String stateId = stateIdByName.get(pickedDisplay); // map by translated display text
            if (stateId != null) {
                preloadDistrictUi();
                fetchDistricts(stateId);
            }
        });

        rgPlaceType.setOnCheckedChangeListener((group, checkedIds) -> {
            boolean isVillage = new HashSet<>(checkedIds).contains(R.id.rbVillage);
            String hint = isVillage ? I18n.t(this, "Village name") : I18n.t(this, "City name");
            ((TextInputLayout) etPlaceName.getParent().getParent()).setHint(hint);
        });

        TextWatcher watcher = new SimpleTextWatcher(() -> btnContinue.setEnabled(isFormBasicsValid() && !isSendingOtp));
        etName.addTextChangedListener(watcher);
        etSurname.addTextChangedListener(watcher);
        etMobile.addTextChangedListener(watcher);
        etPlaceName.addTextChangedListener(watcher);
        etAddress.addTextChangedListener(watcher);
        etPincode.addTextChangedListener(watcher);
        cbTerms.setOnCheckedChangeListener((b, c) -> btnContinue.setEnabled(isFormBasicsValid() && !isSendingOtp));

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });

        btnContinue.setOnClickListener(v -> {
            if (!validateAll())
                return;
            if (isSendingOtp)
                return;
            sendOtp(textOf(etMobile));
        });
    }

    private void bindViews() {
        tvLangBadge = findViewById(R.id.tvLangBadge);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        tvTitle = findViewById(R.id.tvTitle);
        etName = findViewById(R.id.etName);
        etSurname = findViewById(R.id.etSurname);
        etMobile = findViewById(R.id.etMobile);
        spState = findViewById(R.id.spState);
        spDistrict = findViewById(R.id.spDistrict);
        rgPlaceType = findViewById(R.id.rgPlaceType);
        etPlaceName = findViewById(R.id.etPlaceName);
        etAddress = findViewById(R.id.etAddress);
        etPincode = findViewById(R.id.etPincode);
        cbTerms = findViewById(R.id.cbTerms);
        btnContinue = findViewById(R.id.btnContinue);
        tvLangBadge.setText(I18n.t(this, "Language") + ": " + new SessionManager(this).getLangName());
    }

    /** Fix status bar overlap by applying system bar top inset as padding. */
    private void applyStatusBarInsets() {
        final View root = findViewById(R.id.rootUserInfo);
        if (root == null)
            return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bar = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bar.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /* ------------ dynamic states/districts ----------- */
    private void preloadStateUi() {
        stateNames.clear();
        stateIdByName.clear();
        String loading = I18n.t(this, "Loading states…");
        stateNames.add(loading);
        spState.setText(loading, false);
        spState.setEnabled(false);
        stateAdapter.notifyDataSetChanged();
    }

    private void preloadDistrictUi() {
        districtNames.clear();
        districtIdByName.clear();
        String loading = I18n.t(this, "Loading districts…");
        districtNames.add(loading);
        spDistrict.setText(loading, false);
        spDistrict.setEnabled(false);
        districtAdapter.notifyDataSetChanged();
    }

    private void fetchStates() {
        // Keep loader visible (already showing from prefetch)
        showLoading(I18n.t(this, "Loading states…"));

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, ApiRoutes.GET_STATES, null,
                resp -> {
                    boolean ok = resp.optBoolean("ok", false);
                    JSONArray data = resp.optJSONArray("data");
                    if (ok && data != null && data.length() > 0) {
                        final ArrayList<String> enNames = new ArrayList<>();
                        final ArrayList<String> ids = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject it = data.optJSONObject(i);
                            if (it == null)
                                continue;
                            String id = it.optString("id", "");
                            String name = it.optString("name", "");
                            if (!id.isEmpty() && !name.isEmpty()) {
                                enNames.add(name);
                                ids.add(id);
                            }
                        }
                        translateListAsync(enNames, translated -> {
                            stateNames.clear();
                            stateIdByName.clear();
                            for (int i = 0; i < translated.size(); i++) {
                                String display = translated.get(i);
                                String id = ids.get(i);
                                stateNames.add(display);
                                stateIdByName.put(display, id);
                            }
                            spState.setEnabled(true);
                            spState.setText("", false);
                            stateAdapter.notifyDataSetChanged();
                            hideLoading();
                        }, () -> {
                            // Fallback to English if translation fails
                            stateNames.clear();
                            stateIdByName.clear();
                            for (int i = 0; i < enNames.size(); i++) {
                                String display = enNames.get(i);
                                String id = ids.get(i);
                                stateNames.add(display);
                                stateIdByName.put(display, id);
                            }
                            spState.setEnabled(true);
                            spState.setText("", false);
                            stateAdapter.notifyDataSetChanged();
                            hideLoading();
                        });
                    } else {
                        stateNames.clear();
                        stateNames.add(I18n.t(this, "No states found"));
                        spState.setEnabled(false);
                        stateAdapter.notifyDataSetChanged();
                        hideLoading();
                    }
                },
                err -> {
                    stateNames.clear();
                    stateNames.add(I18n.t(this, "Failed to load states"));
                    spState.setEnabled(false);
                    stateAdapter.notifyDataSetChanged();
                    hideLoading();
                });
        req.setShouldCache(false); // Always fetch fresh state data
        req.setRetryPolicy(new DefaultRetryPolicy(12000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }

    private void fetchDistricts(String stateId) {
        showLoading(I18n.t(this, "Loading districts…"));

        String url = ApiRoutes.GET_DISTRICTS + "?state_id=" + stateId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                resp -> {
                    boolean ok = resp.optBoolean("ok", false);
                    JSONArray data = resp.optJSONArray("data");
                    if (ok && data != null && data.length() > 0) {
                        final ArrayList<String> enNames = new ArrayList<>();
                        final ArrayList<String> ids = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject it = data.optJSONObject(i);
                            if (it == null)
                                continue;
                            String id = it.optString("id", "");
                            String name = it.optString("name", "");
                            if (!id.isEmpty() && !name.isEmpty()) {
                                enNames.add(name);
                                ids.add(id);
                            }
                        }
                        translateListAsync(enNames, translated -> {
                            districtNames.clear();
                            districtIdByName.clear();
                            for (int i = 0; i < translated.size(); i++) {
                                String display = translated.get(i);
                                String id = ids.get(i);
                                districtNames.add(display);
                                districtIdByName.put(display, id);
                            }
                            spDistrict.setEnabled(true);
                            spDistrict.setText("", false);
                            districtAdapter.notifyDataSetChanged();
                            hideLoading();
                        }, () -> {
                            // Fallback to English
                            districtNames.clear();
                            districtIdByName.clear();
                            for (int i = 0; i < enNames.size(); i++) {
                                String display = enNames.get(i);
                                String id = ids.get(i);
                                districtNames.add(display);
                                districtIdByName.put(display, id);
                            }
                            spDistrict.setEnabled(true);
                            spDistrict.setText("", false);
                            districtAdapter.notifyDataSetChanged();
                            hideLoading();
                        });
                    } else {
                        districtNames.clear();
                        districtNames.add(I18n.t(this, "No districts found"));
                        spDistrict.setEnabled(false);
                        districtAdapter.notifyDataSetChanged();
                        hideLoading();
                    }
                },
                err -> {
                    districtNames.clear();
                    districtNames.add(I18n.t(this, "Failed to load districts"));
                    spDistrict.setEnabled(false);
                    districtAdapter.notifyDataSetChanged();
                    hideLoading();
                });
        req.setShouldCache(false); // Always fetch fresh district data
        req.setRetryPolicy(new DefaultRetryPolicy(12000, 0, 1f));
        VolleySingleton.getInstance(this).add(req);
    }

    /*
     * -------------------------------- validation --------------------------------
     */
    private boolean isFormBasicsValid() {
        return !TextUtils.isEmpty(etName.getText())
                && !TextUtils.isEmpty(etSurname.getText())
                && etMobile.getText() != null
                && etMobile.getText().toString().trim().matches("^[6-9]\\d{9}$")
                && !TextUtils.isEmpty(spState.getText())
                && !TextUtils.isEmpty(spDistrict.getText())
                && !TextUtils.isEmpty(etPlaceName.getText())
                && !TextUtils.isEmpty(etAddress.getText())
                && etPincode.getText() != null
                && etPincode.getText().toString().trim().matches("^\\d{6}$")
                && cbTerms.isChecked();
    }

    private boolean validateAll() {
        if (etName.getText() == null || textOf(etName).length() < 2) {
            ((TextInputLayout) etName.getParent().getParent()).setError(I18n.t(this, "Enter valid name"));
            etName.requestFocus();
            return false;
        }
        if (etSurname.getText() == null || textOf(etSurname).length() < 2) {
            ((TextInputLayout) etSurname.getParent().getParent()).setError(I18n.t(this, "Enter valid surname"));
            etSurname.requestFocus();
            return false;
        }
        if (etMobile.getText() == null || !textOf(etMobile).matches("^[6-9]\\d{9}$")) {
            ((TextInputLayout) etMobile.getParent().getParent()).setError(I18n.t(this, "Enter 10-digit Indian mobile"));
            etMobile.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(spState.getText())) {
            ((TextInputLayout) spState.getParent().getParent()).setError(I18n.t(this, "Select state"));
            spState.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(spDistrict.getText())) {
            ((TextInputLayout) spDistrict.getParent().getParent()).setError(I18n.t(this, "Select district"));
            spDistrict.requestFocus();
            return false;
        }
        if (etPlaceName.getText() == null || textOf(etPlaceName).length() < 2) {
            ((TextInputLayout) etPlaceName.getParent().getParent()).setError(I18n.t(this, "Enter village/city"));
            etPlaceName.requestFocus();
            return false;
        }
        if (etAddress.getText() == null || textOf(etAddress).length() < 5) {
            ((TextInputLayout) etAddress.getParent().getParent()).setError(I18n.t(this, "Enter full address"));
            etAddress.requestFocus();
            return false;
        }
        if (etPincode.getText() == null || !textOf(etPincode).matches("^\\d{6}$")) {
            ((TextInputLayout) etPincode.getParent().getParent()).setError(I18n.t(this, "Enter 6-digit pincode"));
            etPincode.requestFocus();
            return false;
        }
        if (!cbTerms.isChecked()) {
            showToast(I18n.t(this, "Please accept Terms & Conditions"));
            return false;
        }
        // clear errors
        ((TextInputLayout) etName.getParent().getParent()).setError(null);
        ((TextInputLayout) etSurname.getParent().getParent()).setError(null);
        ((TextInputLayout) etMobile.getParent().getParent()).setError(null);
        ((TextInputLayout) spState.getParent().getParent()).setError(null);
        ((TextInputLayout) spDistrict.getParent().getParent()).setError(null);
        ((TextInputLayout) etPlaceName.getParent().getParent()).setError(null);
        ((TextInputLayout) etAddress.getParent().getParent()).setError(null);
        ((TextInputLayout) etPincode.getParent().getParent()).setError(null);
        return true;
    }

    /*
     * -------------------------------- register → OTP
     * --------------------------------
     */
    private void setSending(boolean v) {
        isSendingOtp = v;
        btnContinue.setEnabled(!v && isFormBasicsValid());
        if (v) {
            btnContinue.setText(I18n.t(this, "Loading…"));
            showLoading(I18n.t(this, "Loading…"));
        } else {
            btnContinue.setText(I18n.t(this, "Continue"));
            hideLoading();
        }
    }

    private void sendOtp(String mobile) {
        if (isSendingOtp)
            return;
        setSending(true);

        try {
            JSONObject body = new JSONObject();
            body.put("phone", mobile);
            body.put("flow", "register"); // key behavior

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, ApiRoutes.SEND_OTP, body,
                    resp -> {
                        setSending(false);
                        boolean userExists = resp.optBoolean("user_exists", false);
                        String next = resp.optString("next", "");
                        String errorCode = resp.optString("error_code", "");

                        if (userExists || "login_required".equalsIgnoreCase(next)
                                || "ALREADY_REGISTERED".equalsIgnoreCase(errorCode)) {
                            showToast(I18n.t(this, "You are already registered. Please login."));
                            goToLoginWithPhone(mobile);
                            return;
                        }

                        boolean otpSent = resp.optBoolean("otp_sent", false);
                        if (otpSent) {
                            showOtpSheet(mobile);
                        } else {
                            showToast(I18n.t(this, "Failed to send OTP"));
                        }
                    },
                    err -> {
                        setSending(false);
                        showToast(I18n.t(this, readableVolleyError(err)));
                    }) {
                @Override
                public java.util.Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json; charset=utf-8");
                    return h;
                }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
            VolleySingleton.getInstance(this).add(req);
        } catch (JSONException e) {
            setSending(false);
            showToast("JSON error: " + e.getMessage());
        }
    }

    private void verifyOtp(String mobile, String otp) {
        if (isVerifyingOtp)
            return;
        isVerifyingOtp = true;

        try {
            JSONObject body = new JSONObject();
            body.put("phone", mobile);
            body.put("otp", otp);
            body.put("device", "Android/" + android.os.Build.MODEL);

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, ApiRoutes.VERIFY_OTP, body,
                    resp -> {
                        isVerifyingOtp = false;
                        boolean ok = resp.optBoolean("ok", false);
                        if (!ok) {
                            setOtpInlineError(I18n.t(this, "Invalid/expired OTP"));
                            return;
                        }
                        String access = resp.optString("access_token", null);
                        String refresh = resp.optString("refresh_token", null);
                        int userId = resp.optInt("user_id", -1);
                        boolean isNew = resp.optBoolean("is_new", true);
                        int expiresIn = resp.optInt("expires_in", 0);

                        if (access != null && refresh != null && userId > 0 && expiresIn > 0) {
                            session.saveTokens(access, refresh, userId);
                            saveAuthToPrefs(expiresIn);

                            session.setOnboarded(true);
                            if (resendTimer != null)
                                resendTimer.cancel();
                            if (otpDialog != null)
                                otpDialog.dismiss();

                            if (isNew)
                                saveProfileThenGoHome();
                            else {
                                startActivity(new Intent(this, MainActivity.class));
                                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                finish();
                            }
                        } else {
                            setOtpInlineError(I18n.t(this, "Login failed: bad response"));
                        }
                    },
                    err -> {
                        isVerifyingOtp = false;
                        setOtpInlineError(I18n.t(this, "Network error"));
                    }) {
                @Override
                public java.util.Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json; charset=utf-8");
                    String token = session.getAccessToken();
                    if (token != null)
                        h.put("Authorization", "Bearer " + token);
                    return h;
                }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
            VolleySingleton.getInstance(this).add(req);
        } catch (JSONException e) {
            isVerifyingOtp = false;
            setOtpInlineError("JSON error");
        }
    }

    private void saveProfileThenGoHome() {
        if (isSavingProfile)
            return;
        isSavingProfile = true;
        showLoading(I18n.t(this, "Saving profile…"));

        try {
            String name = textOf(etName);
            String surname = textOf(etSurname);
            String full = name + (isBlank(surname) ? "" : (" " + surname));
            String phone = textOf(etMobile);
            String state = spState.getText() == null ? "" : spState.getText().toString().trim();
            String dist = spDistrict.getText() == null ? "" : spDistrict.getText().toString().trim();
            String place = textOf(etPlaceName);
            String addr = textOf(etAddress);
            String pin = textOf(etPincode);

            String placeType = null;
            int checkedId = rgPlaceType.getCheckedChipId();
            if (checkedId == R.id.rbVillage)
                placeType = "village";
            else if (checkedId == R.id.rbCity)
                placeType = "city";

            if (isBlank(full)) {
                isSavingProfile = false;
                hideLoading();
                ((TextInputLayout) etName.getParent().getParent()).setError(I18n.t(this, "Enter valid name"));
                etName.requestFocus();
                return;
            }
            if (!phone.matches("^[6-9]\\d{9}$")) {
                isSavingProfile = false;
                hideLoading();
                ((TextInputLayout) etMobile.getParent().getParent())
                        .setError(I18n.t(this, "Enter 10-digit Indian mobile"));
                etMobile.requestFocus();
                return;
            }
            if (!isBlank(pin) && !pin.matches("^\\d{6}$")) {
                isSavingProfile = false;
                hideLoading();
                ((TextInputLayout) etPincode.getParent().getParent()).setError(I18n.t(this, "Enter 6-digit pincode"));
                etPincode.requestFocus();
                return;
            }

            JSONObject body = new JSONObject();
            body.put("full_name", full.trim());
            body.put("phone", phone);
            jsonPutIfNotBlank(body, "surname", surname);
            jsonPutIfNotBlank(body, "state", state);
            jsonPutIfNotBlank(body, "district", dist);
            if (placeType != null)
                body.put("place_type", placeType);
            jsonPutIfNotBlank(body, "village_name", place);
            jsonPutIfNotBlank(body, "address", addr);
            jsonPutIfNotBlank(body, "pincode", pin);

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, ApiRoutes.SAVE_PROFILE, body,
                    resp -> {
                        isSavingProfile = false;
                        hideLoading();
                        boolean ok = resp.optBoolean("ok", false);
                        if (!ok) {
                            String msg = firstNonEmpty(resp.optString("message", "").trim(),
                                    resp.optString("error", "Profile save failed").trim());
                            showToast(I18n.t(this, msg.isEmpty() ? "Profile save failed" : msg));
                            return;
                        }
                        startActivity(new Intent(this, MainActivity.class));
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        finish();
                    },
                    err -> {
                        isSavingProfile = false;
                        hideLoading();
                        showToast(I18n.t(this, readableVolleyError(err)));
                    }) {
                @Override
                public java.util.Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json; charset=utf-8");
                    String token = session.getAccessToken();
                    if (token != null)
                        h.put("Authorization", "Bearer " + token);
                    return h;
                }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 0, 1.0f));
            VolleySingleton.getInstance(this).add(req);

        } catch (JSONException e) {
            isSavingProfile = false;
            hideLoading();
            showToast("JSON error: " + e.getMessage());
        }
    }

    /* --------------------------- OTP bottom sheet --------------------------- */
    @SuppressLint("SetTextI18n")
    private void showOtpSheet(String mobile) {
        if (otpDialog != null && otpDialog.isShowing())
            otpDialog.dismiss();
        if (resendTimer != null)
            resendTimer.cancel();

        otpDialog = new BottomSheetDialog(this,
                com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_otp, null, false);
        otpDialog.setContentView(sheet);
        otpDialog.setCancelable(false);
        otpDialog.setCanceledOnTouchOutside(false);

        TextView tvMobile = sheet.findViewById(R.id.tvMobile);
        TextView tvTimer = sheet.findViewById(R.id.tvTimer);
        TextView tvResend = sheet.findViewById(R.id.tvResend);
        View btnVerify = sheet.findViewById(R.id.btnVerify);
        View btnClose = sheet.findViewById(R.id.btnClose);

        EditText d1 = sheet.findViewById(R.id.d1);
        EditText d2 = sheet.findViewById(R.id.d2);
        EditText d3 = sheet.findViewById(R.id.d3);
        EditText d4 = sheet.findViewById(R.id.d4);
        EditText d5 = sheet.findViewById(R.id.d5);
        EditText d6 = sheet.findViewById(R.id.d6);

        tvMobile.setText(I18n.t(this, "OTP sent to") + " +91 " + mobile);

        setupOtpInputs(d1, d2, d3, d4, d5, d6);
        startResendTimer(tvTimer, tvResend);

        tvResend.setOnClickListener(v -> {
            if (!tvResend.isEnabled())
                return;
            clearOtp(d1, d2, d3, d4, d5, d6);
            d1.requestFocus();
            startResendTimer(tvTimer, tvResend);
            if (!isSendingOtp)
                sendOtp(mobile);
        });

        btnVerify.setOnClickListener(v -> {
            String code = get(d1) + get(d2) + get(d3) + get(d4) + get(d5) + get(d6);
            if (code.length() != 6) {
                d6.setError(I18n.t(this, "Enter 6 digits"));
                d6.requestFocus();
                return;
            }
            verifyOtp(mobile, code);
        });

        btnClose.setOnClickListener(v -> {
            if (resendTimer != null)
                resendTimer.cancel();
            otpDialog.dismiss();
        });

        otpDialog.show();
        d1.requestFocus();
        if (otpDialog.getWindow() != null) {
            otpDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            otpDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void setOtpInlineError(String msg) {
        if (otpDialog == null)
            return;
        TextView tvTimer = otpDialog.findViewById(R.id.tvTimer);
        if (tvTimer != null)
            tvTimer.setText(msg);
    }

    private void setupOtpInputs(EditText... boxes) {
        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            EditText et = boxes[i];
            et.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(1) });
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {
                }

                @Override
                public void onTextChanged(CharSequence s, int st, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s != null && s.length() == 1 && index < boxes.length - 1)
                        boxes[index + 1].requestFocus();
                }
            });
            et.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DEL
                        && et.getText() != null && et.getText().length() == 0
                        && index > 0) {
                    boxes[index - 1].requestFocus();
                    boxes[index - 1].setText("");
                    return true;
                }
                return false;
            });
        }
    }

    private void startResendTimer(TextView tvTimer, TextView tvResend) {
        tvResend.setEnabled(false);
        tvResend.setAlpha(0.5f);
        if (resendTimer != null)
            resendTimer.cancel();
        resendTimer = new CountDownTimer(30_000, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(I18n.t(UserInfoActivity.this, "Resend in") + " " + (ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                tvTimer.setText("");
                tvResend.setEnabled(true);
                tvResend.setAlpha(1f);
            }
        }.start();
    }

    /* -------------------------------- utils -------------------------------- */
    private static void clearOtp(EditText... boxes) {
        for (EditText e : boxes)
            e.setText("");
    }

    private static String get(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static String textOf(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void jsonPutIfNotBlank(JSONObject o, String key, String val) throws JSONException {
        if (val != null) {
            String t = val.trim();
            if (!t.isEmpty())
                o.put(key, t);
        }
    }

    private String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : (b == null ? "" : b);
    }

    private String readableVolleyError(VolleyError err) {
        if (err == null)
            return "Network error";
        NetworkResponse nr = err.networkResponse;
        if (nr != null) {
            int code = nr.statusCode;
            String body;
            try {
                body = new String(nr.data, StandardCharsets.UTF_8);
            } catch (Exception e) {
                body = "";
            }
            try {
                JSONObject o = new JSONObject(body);
                String msg = firstNonEmpty(o.optString("message", "").trim(), o.optString("error", "").trim());
                String codeStr = o.optString("error_code", "");
                if ("ALREADY_REGISTERED".equalsIgnoreCase(codeStr))
                    return "You are already registered. Please login.";
                if ("NOT_REGISTERED".equalsIgnoreCase(codeStr))
                    return "This number is not registered.";
                if ("RATE_LIMITED".equalsIgnoreCase(codeStr))
                    return msg.isEmpty() ? "Too many OTP requests. Please wait." : msg;
                if (!msg.isEmpty())
                    return "HTTP " + code + " - " + msg;
            } catch (Exception ignored) {
            }
            if (code >= 500)
                return "Server unavailable. Please try again.";
            return "HTTP " + code;
        }
        return "Network error: " + err.getClass().getSimpleName();
    }

    private void showLoading(String message) {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            loadingDialog.setContentView(R.layout.dialog_loading_text_only);
            loadingDialog.setCancelable(false);
            loadingDialog.setCanceledOnTouchOutside(false);
            if (loadingDialog.getWindow() != null)
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView tv = loadingDialog.findViewById(R.id.tvLoadingMsg);
        if (tv != null)
            tv.setText(message == null ? I18n.t(this, "Loading…") : message);
        if (!loadingDialog.isShowing())
            loadingDialog.show();
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing())
            loadingDialog.dismiss();
    }

    private void showToast(String msg) {
        if (msg == null || msg.trim().isEmpty())
            return;
        if (singleToast == null)
            singleToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
        else {
            singleToast.setText(msg);
            singleToast.setDuration(Toast.LENGTH_SHORT);
        }
        singleToast.show();
    }

    private void goToLoginWithPhone(String phone) {
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra("prefill_phone", phone);
        startActivity(i);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void saveAuthToPrefs(int expiresInSeconds) {
        long expAt = (System.currentTimeMillis() / 1000L) + Math.max(0, expiresInSeconds);
        SharedPreferences sp = getSharedPreferences(SplashScreen.PREFS, MODE_PRIVATE);
        sp.edit()
                .putString(SplashScreen.KEY_ACCESS, session.getAccessToken())
                .putLong(SplashScreen.KEY_ACCESS_EXP, expAt)
                .putBoolean(SplashScreen.KEY_ONBOARDED, true)
                .apply();
    }

    static class SimpleTextWatcher implements TextWatcher {
        interface OnChange {
            void run();
        }

        private final OnChange cb;

        SimpleTextWatcher(OnChange c) {
            this.cb = c;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (cb != null)
                cb.run();
        }
    }

    /* =================== Translation helpers for lists =================== */

    /**
     * Translates a list of English strings to the current language.
     * Uses same cache as I18n; very fast on repeat.
     */
    @SuppressLint("NewApi")
    private void translateListAsync(ArrayList<String> english,
            java.util.function.Consumer<ArrayList<String>> onSuccess,
            Runnable onError) {
        final String target = I18n.lang(this);
        if ("en".equalsIgnoreCase(target) || english.isEmpty()) {
            onSuccess.accept(new ArrayList<>(english));
            return;
        }

        // Try cache first
        final ArrayList<String> result = new ArrayList<>(english.size());
        final ArrayList<String> needNetwork = new ArrayList<>();
        final java.util.HashMap<String, Integer> indexMap = new java.util.HashMap<>();

        SharedPreferences sp = getSharedPreferences("i18n_cache_v1", MODE_PRIVATE);
        for (int i = 0; i < english.size(); i++) {
            String en = english.get(i);
            String key = "v1|" + target + "|" + en.trim();
            String cached = sp.getString(key, null);
            if (cached != null) {
                result.add(cached);
            } else {
                indexMap.put(en, i);
                result.add(null);
                needNetwork.add(en);
            }
        }

        if (needNetwork.isEmpty()) {
            onSuccess.accept(result);
            return;
        }

        // Build x-www-form-urlencoded body
        StringBuilder body = new StringBuilder();
        try {
            for (String s : needNetwork) {
                if (body.length() > 0)
                    body.append("&");
                body.append("q=").append(URLEncoder.encode(s, "UTF-8"));
            }
            if (body.length() > 0)
                body.append("&");
            body.append("target=").append(URLEncoder.encode(target, "UTF-8"));
            body.append("&format=text");
            body.append("&key=").append(URLEncoder.encode(GOOGLE_TRANSLATE_API_KEY, "UTF-8"));
        } catch (Exception e) {
            if (onError != null)
                onError.run();
            return;
        }

        @SuppressLint({ "NewApi", "LocalSuppress" })
        StringRequest req = new StringRequest(
                Request.Method.POST,
                G_TRANSLATE_URL,
                resp -> {
                    try {
                        JSONObject o = new JSONObject(resp);
                        JSONObject data = o.optJSONObject("data");
                        JSONArray arr = data != null ? data.optJSONArray("translations") : null;
                        if (arr == null) {
                            if (onError != null)
                                onError.run();
                            return;
                        }
                        // Map in order to original "needNetwork" list
                        SharedPreferences.Editor ed = sp.edit();
                        for (int i = 0; i < arr.length() && i < needNetwork.size(); i++) {
                            String en = needNetwork.get(i);
                            JSONObject ti = arr.optJSONObject(i);
                            if (ti == null)
                                continue;
                            String translated = ti.optString("translatedText", null);
                            if (translated == null)
                                continue;
                            String plain = Html.fromHtml(translated, Html.FROM_HTML_MODE_LEGACY).toString();

                            Integer pos = indexMap.get(en);
                            if (pos != null)
                                result.set(pos, plain);

                            String k = "v1|" + target + "|" + en.trim();
                            ed.putString(k, plain);
                        }
                        ed.apply();

                        // Fill any remaining nulls with English fallback
                        for (int i = 0; i < result.size(); i++) {
                            if (result.get(i) == null)
                                result.set(i, english.get(i));
                        }
                        onSuccess.accept(result);
                    } catch (Exception e) {
                        if (onError != null)
                            onError.run();
                    }
                },
                err -> {
                    if (onError != null)
                        onError.run();
                }) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return body.toString().getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public java.util.Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> h = new HashMap<>();
                h.put("Accept", "application/json");
                return h;
            }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(12000, 1, 1.0f));
        VolleySingleton.getInstance(this).add(req);
    }
}
