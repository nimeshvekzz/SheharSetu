package com.infowave.sheharsetu;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.core.SessionManager;
import com.infowave.sheharsetu.net.ApiRoutes;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etMobile;
    private MaterialButton btnSendOtp;
    private TextView tvGoRegister, tvLangBadge, tvTitle;

    private BottomSheetDialog otpDialog;
    private CountDownTimer resendTimer;

    private SessionManager session;

    // Debounce + inline loading state
    private boolean isSendingOtp = false;
    private String btnIdleText; // original button label

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply chosen language before inflating views
        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        setContentView(R.layout.activity_login);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.BLACK);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        session = new SessionManager(this);

        etMobile     = findViewById(R.id.etMobile);
        btnSendOtp   = findViewById(R.id.btnSendOtp);
        tvGoRegister = findViewById(R.id.tvGoRegister);
        tvLangBadge  = findViewById(R.id.tvLangBadge);
        tvTitle      = findViewById(R.id.tvTitle);

        // Prefetch common phrases used on this screen (use overload with Runnable)
        I18n.prefetch(this, java.util.Arrays.asList(
                "Language",
                "Login",
                "Send OTP",
                "Loading…",
                "Enter 10-digit mobile",
                "Not registered. Please register.",
                "Failed to send OTP",
                "Network error",
                "Login failed",
                "OTP sent to",
                "Enter 6 digits",
                "Resend in",
                "Already have an account? Log in"
        ), () -> { /* no-op: warm cache */ });

        // Translate static labels
        if (tvLangBadge != null) {
            tvLangBadge.setText(I18n.t(this, "Language") + ": " + session.getLangName());
        }
        if (tvTitle != null) I18n.translateAndApplyText(tvTitle, this);
        if (tvGoRegister != null) I18n.translateAndApplyText(tvGoRegister, this);

        // Translate hint on the phone field (via its TextInputLayout)
        View til = etMobile.getParent() != null ? (View) etMobile.getParent().getParent() : null;
        if (til instanceof TextInputLayout) {
            I18n.translateAndApplyHint((TextInputLayout) til, this); // uses current hint text
        }

        // Store initial button label (translated)
        btnIdleText = btnSendOtp.getText() == null
                ? I18n.t(this, "Send OTP")
                : I18n.t(this, btnSendOtp.getText().toString());
        btnSendOtp.setText(btnIdleText);

        if (getIntent().hasExtra("prefill_phone")) {
            String pre = getIntent().getStringExtra("prefill_phone");
            if (pre != null) etMobile.setText(pre);
        }

        btnSendOtp.setOnClickListener(v -> {
            if (isSendingOtp) return; // debounce

            String m = etMobile.getText()==null ? "" : etMobile.getText().toString().trim();
            if (!m.matches("^[6-9]\\d{9}$")) {
                etMobile.setError(I18n.t(this, "Enter 10-digit mobile"));
                etMobile.requestFocus();
                return;
            }

            setSending(true);  // inline "Loading…" state (no external loader)
            sendOtpToServer(m);
        });

        tvGoRegister.setOnClickListener(v -> {
            if (isSendingOtp) return; // optional: block while sending
            startActivity(new Intent(this, UserInfoActivity.class));
            finish();
        });
    }

    // Toggle inline loading on the CTA button
    private void setSending(boolean sending) {
        isSendingOtp = sending;
        btnSendOtp.setEnabled(!sending);
        if (sending) {
            if (btnIdleText == null) btnIdleText = I18n.t(this, "Send OTP");
            btnSendOtp.setText(I18n.t(this, "Loading…"));
        } else {
            btnSendOtp.setText(btnIdleText == null ? I18n.t(this, "Send OTP") : btnIdleText);
        }
    }

    private void sendOtpToServer(String mobile) {
        try {
            JSONObject body = new JSONObject();
            body.put("phone", mobile);
            body.put("flow", "login"); // IMPORTANT: login flow

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, ApiRoutes.SEND_OTP, body,
                    resp -> {
                        boolean ok = resp.optBoolean("ok", false);
                        String next = resp.optString("next", "");
                        String errorCode = resp.optString("error_code", "");
                        boolean userExists = resp.optBoolean("user_exists", false);

                        if (!ok && "NOT_REGISTERED".equalsIgnoreCase(errorCode)) {
                            Toast.makeText(this, I18n.t(this, "Not registered. Please register."), Toast.LENGTH_SHORT).show();
                            setSending(false);
                            startActivity(new Intent(this, UserInfoActivity.class));
                            finish();
                            return;
                        }

                        // Registered users → open OTP sheet
                        if (userExists || "login_flow".equalsIgnoreCase(next) || ok) {
                            showOtpSheet(mobile); // will call setSending(false)
                        } else {
                            Toast.makeText(this, I18n.t(this, "Failed to send OTP"), Toast.LENGTH_SHORT).show();
                            setSending(false);
                        }
                    },
                    err -> {
                        Toast.makeText(this, I18n.t(this, "Network error"), Toast.LENGTH_SHORT).show();
                        setSending(false);
                    }
            ){
                @Override public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json; charset=utf-8");
                    // Hint server for localized responses (if supported)
                    h.put("Accept-Language", I18n.lang(LoginActivity.this));
                    return h;
                }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1.0f));
            VolleySingleton.getInstance(this).add(req);
        } catch (JSONException e) {
            Toast.makeText(this, "JSON error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            setSending(false);
        }
    }

    /* ===================== OTP Bottom Sheet ===================== */
    @SuppressLint("SetTextI18n")
    private void showOtpSheet(String mobile) {
        if (otpDialog != null && otpDialog.isShowing()) otpDialog.dismiss();
        if (resendTimer != null) resendTimer.cancel();

        otpDialog = new BottomSheetDialog(this, com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_otp, null, false);
        otpDialog.setContentView(sheet);
        otpDialog.setCancelable(false);
        otpDialog.setCanceledOnTouchOutside(false);

        TextView tvMobile = sheet.findViewById(R.id.tvMobile);
        TextView tvTimer  = sheet.findViewById(R.id.tvTimer);
        TextView tvResend = sheet.findViewById(R.id.tvResend);
        View btnVerify    = sheet.findViewById(R.id.btnVerify);
        View btnClose     = sheet.findViewById(R.id.btnClose);

        EditText d1 = sheet.findViewById(R.id.d1);
        EditText d2 = sheet.findViewById(R.id.d2);
        EditText d3 = sheet.findViewById(R.id.d3);
        EditText d4 = sheet.findViewById(R.id.d4);
        EditText d5 = sheet.findViewById(R.id.d5);
        EditText d6 = sheet.findViewById(R.id.d6);

        tvMobile.setText(I18n.t(this, "OTP sent to") + " " + mobile);

        setupOtpInputs(d1, d2, d3, d4, d5, d6);
        startResendTimer(tvTimer, tvResend);

        tvResend.setOnClickListener(v -> {
            if (!tvResend.isEnabled()) return;
            clearOtp(d1, d2, d3, d4, d5, d6);
            d1.requestFocus();
            startResendTimer(tvTimer, tvResend);
            // Resend from sheet should NOT touch the main button's state
            sendOtpToServer(mobile);
        });

        btnVerify.setOnClickListener(v -> {
            String code = get(d1) + get(d2) + get(d3) + get(d4) + get(d5) + get(d6);
            if (code.length() != 6) { d6.setError(I18n.t(this, "Enter 6 digits")); d6.requestFocus(); return; }
            verifyOtpOnServer(mobile, code, tvTimer);
        });

        btnClose.setOnClickListener(v -> {
            if (resendTimer != null) resendTimer.cancel();
            otpDialog.dismiss();
        });

        // Showing the sheet → release the button from Loading…
        setSending(false);

        otpDialog.show();
        d1.requestFocus();
        if (otpDialog.getWindow() != null) {
            otpDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            otpDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void verifyOtpOnServer(String mobile, String otp, TextView errorField) {
        try {
            JSONObject body = new JSONObject();
            body.put("phone", mobile);
            body.put("otp", otp);
            body.put("device", "Android/" + android.os.Build.MODEL);

            JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, ApiRoutes.VERIFY_OTP, body,
                    resp -> {
                        boolean ok = resp.optBoolean("ok", false);
                        if (!ok) {
                            if (errorField != null) errorField.setText(I18n.t(this, "Invalid/expired OTP"));
                            return;
                        }

                        String access   = resp.optString("access_token", null);
                        String refresh  = resp.optString("refresh_token", null);
                        int userId      = resp.optInt("user_id", -1);
                        int expiresIn   = resp.optInt("expires_in", 0); // seconds

                        if (access != null && refresh != null && userId > 0 && expiresIn > 0) {
                            // Session tokens save
                            session.saveTokens(access, refresh, userId);
                            saveAuthToPrefs(expiresIn);

                            // 🔴 IMPORTANT: yahan se DynamicFormActivity ko user_id milega
                            SharedPreferences userPrefs = getSharedPreferences("user", MODE_PRIVATE);
                            userPrefs.edit()
                                    .putLong("user_id", userId)
                                    .apply();

                            if (resendTimer != null) resendTimer.cancel();
                            if (otpDialog != null) otpDialog.dismiss();
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        } else {
                            if (errorField != null) errorField.setText(I18n.t(this, "Login failed"));
                        }
                    },
                    err -> {
                        if (errorField != null) errorField.setText(I18n.t(this, "Network error"));
                    }
            ){
                @Override public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Content-Type", "application/json; charset=utf-8");
                    // Keep language consistent across flows
                    h.put("Accept-Language", I18n.lang(LoginActivity.this));
                    return h;
                }
            };
            req.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1.0f));
            VolleySingleton.getInstance(this).add(req);
        } catch (JSONException e) {
            if (errorField != null) errorField.setText("JSON error");
        }
    }

    private void saveAuthToPrefs(int expiresInSeconds) {
        long expAt = (System.currentTimeMillis()/1000L) + Math.max(0, expiresInSeconds);
        SharedPreferences sp = getSharedPreferences(SplashScreen.PREFS, MODE_PRIVATE);
        sp.edit()
                .putString(SplashScreen.KEY_ACCESS, session.getAccessToken())
                .putLong(SplashScreen.KEY_ACCESS_EXP, expAt)
                .putBoolean(SplashScreen.KEY_ONBOARDED, true)
                .apply();
    }

    private void setupOtpInputs(EditText... boxes) {
        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            EditText et = boxes[i];
            et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1)});
            et.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    if (s != null && s.length() == 1 && index < boxes.length - 1) boxes[index + 1].requestFocus();
                }
            });
            et.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DEL &&
                        et.getText() != null && et.getText().length() == 0 &&
                        index > 0) {
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
        if (resendTimer != null) resendTimer.cancel();
        resendTimer = new CountDownTimer(30_000, 1000) {
            @Override public void onTick(long ms) {
                tvTimer.setText(I18n.t(LoginActivity.this, "Resend in") + " " + (ms / 1000) + "s");
            }
            @Override public void onFinish() { tvTimer.setText(""); tvResend.setEnabled(true); tvResend.setAlpha(1f); }
        }.start();
    }

    private static void clearOtp(EditText... boxes) { for (EditText e : boxes) e.setText(""); }
    private static String get(EditText e) { return e.getText() == null ? "" : e.getText().toString().trim(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
        if (otpDialog != null && otpDialog.isShowing()) otpDialog.dismiss();
    }
}
