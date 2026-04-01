package com.infowave.sheharsetu.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

public class SessionManager {
    public static final String PREFS = "sheharsetu_prefs";

    public static final String KEY_LANG_CODE = "app_lang_code";
    public static final String KEY_LANG_NAME = "app_lang_name";
    public static final String KEY_ONBOARDED = "onboarding_done";
    public static final String KEY_LOGGED_IN = "logged_in";

    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_USER_PHONE = "user_phone";

    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ----------------------------- Auth tokens ----------------------------- */
    public void saveTokens(String access, String refresh, int userId) {
        sp.edit()
                .putString(KEY_ACCESS_TOKEN, access)
                .putString(KEY_REFRESH_TOKEN, refresh)
                .putInt(KEY_USER_ID, userId)
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    @Nullable
    public String getAccessToken() {
        return sp.getString(KEY_ACCESS_TOKEN, null);
    }

    @Nullable
    public String getRefreshToken() {
        return sp.getString(KEY_REFRESH_TOKEN, null);
    }

    public int getUserId() {
        return sp.getInt(KEY_USER_ID, -1);
    }

    // User profile cache
    public void saveUserProfile(String name, String phone) {
        sp.edit()
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_PHONE, phone)
                .apply();
    }

    public String getCachedUserName() {
        return sp.getString(KEY_USER_NAME, "User");
    }

    public String getCachedUserPhone() {
        return sp.getString(KEY_USER_PHONE, "");
    }

    public void clearTokens() {
        sp.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .apply();
    }

    public void logout() {
        sp.edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .putBoolean(KEY_LOGGED_IN, false)
                .apply();
    }

    /* ------------------------------ Onboarding ----------------------------- */
    public void setOnboarded(boolean v) {
        sp.edit().putBoolean(KEY_ONBOARDED, v).apply();
    }

    public boolean isOnboarded() {
        return sp.getBoolean(KEY_ONBOARDED, false);
    }

    public void setLoggedIn(boolean v) {
        sp.edit().putBoolean(KEY_LOGGED_IN, v).apply();
    }

    public boolean isLoggedIn() {
        return sp.getBoolean(KEY_LOGGED_IN, false);
    }

    /* -------------------------------- Language ----------------------------- */
    public void setLang(String code, String name) {
        sp.edit()
                .putString(KEY_LANG_CODE, code)
                .putString(KEY_LANG_NAME, name)
                .apply();
    }

    public String getLangName() {
        return sp.getString(KEY_LANG_NAME, "English");
    }

    public String getLangCode() {
        return sp.getString(KEY_LANG_CODE, "en");
    }
}
