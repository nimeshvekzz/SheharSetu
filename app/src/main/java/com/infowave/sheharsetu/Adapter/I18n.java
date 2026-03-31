package com.infowave.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.infowave.sheharsetu.net.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class I18n {

    private static final String TAG = "I18n";

    // Paste your NEW Google Cloud Translation API key here
    private static final String GOOGLE_TRANSLATE_API_KEY = "AIzaSyDnsoOImrpkqOg8Csq9Tawao6BtPQwcr8A";

    private static final String G_URL = "https://translation.googleapis.com/language/translate/v2";
    private static final String SP_NAME = "i18n_cache_v1";
    private static final int VOLLEY_TIMEOUT = 12000;

    private static final int MEM_CACHE_CAP = 500;
    private static final Map<String, String> MEM =
            new LinkedHashMap<String, String>(MEM_CACHE_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MEM_CACHE_CAP;
                }
            };

    private I18n() {}

    public static String lang(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("sheharsetu_prefs", Context.MODE_PRIVATE);
        String code = sp.getString("app_lang_code", "en");
        return (code == null || code.trim().isEmpty()) ? "en" : code.trim();
    }

    public static String t(Context ctx, String key) {
        if (key == null) return "";
        key = key.trim();

        String l = lang(ctx);
        if ("en".equalsIgnoreCase(l) || key.isEmpty()) return key;

        String memKey = "v1|" + l + "|" + key;

        synchronized (MEM) {
            String cached = MEM.get(memKey);
            if (cached != null) return cached;
        }

        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String stored = sp.getString(memKey, null);
        if (stored == null) return key;

        synchronized (MEM) {
            MEM.put(memKey, stored);
        }
        return stored;
    }

    public static void translateAndApplyText(TextView tv, Context ctx) {
        if (tv == null) return;
        CharSequence now = tv.getText();
        String translated = t(ctx, now == null ? "" : now.toString());
        tv.setText(translated);
    }

    public static void translateAndApplyHint(com.google.android.material.textfield.TextInputLayout til, Context ctx) {
        if (til == null) return;
        CharSequence hint = til.getHint();
        String base = hint == null ? "" : hint.toString();
        til.setHint(t(ctx, base));
    }

    public static void prefetch(Context ctx, List<String> keys, Runnable onReady) {
        prefetch(ctx, keys, onReady, null);
    }

    public static void prefetch(Context ctx, List<String> keys, Runnable onReady, Runnable onError) {
        String targetLang = lang(ctx);

        if (keys == null || keys.isEmpty() || "en".equalsIgnoreCase(targetLang)) {
            if (onReady != null) onReady.run();
            return;
        }

        if (GOOGLE_TRANSLATE_API_KEY == null || GOOGLE_TRANSLATE_API_KEY.trim().isEmpty()
                || "PASTE_YOUR_NEW_API_KEY_HERE".equals(GOOGLE_TRANSLATE_API_KEY)) {
            Log.e(TAG, "Google Translate API key is missing or not replaced.");
            if (onError != null) onError.run();
            else if (onReady != null) onReady.run();
            return;
        }

        List<String> unique = new ArrayList<>();
        for (String k : keys) {
            if (k == null) continue;
            k = k.trim();
            if (k.isEmpty()) continue;
            if (!unique.contains(k)) unique.add(k);
        }

        if (unique.isEmpty()) {
            if (onReady != null) onReady.run();
            return;
        }

        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        List<String> missing = new ArrayList<>();

        for (String k : unique) {
            String memKey = "v1|" + targetLang + "|" + k;

            boolean inMem;
            synchronized (MEM) {
                inMem = MEM.containsKey(memKey);
            }

            if (!inMem && sp.getString(memKey, null) == null) {
                missing.add(k);
            }
        }

        if (missing.isEmpty()) {
            if (onReady != null) onReady.run();
            return;
        }

        final String requestUrl;
        final String body;

        try {
            // IMPORTANT FIX:
            // API key must be sent in URL, not in request body
            requestUrl = G_URL + "?key=" + URLEncoder.encode(GOOGLE_TRANSLATE_API_KEY, "UTF-8");

            StringBuilder sb = new StringBuilder();
            for (String s : missing) {
                if (sb.length() > 0) sb.append("&");
                sb.append("q=").append(URLEncoder.encode(s, "UTF-8"));
            }
            sb.append("&target=").append(URLEncoder.encode(targetLang, "UTF-8"));
            sb.append("&format=text");

            body = sb.toString();

            Log.d(TAG, "Translation request URL: " + requestUrl);
            Log.d(TAG, "Target language: " + targetLang + ", missing keys: " + missing.size());

        } catch (Exception e) {
            Log.e(TAG, "Failed to build translation request", e);
            if (onError != null) onError.run();
            else if (onReady != null) onReady.run();
            return;
        }

        StringRequest req = new StringRequest(
                Request.Method.POST,
                requestUrl,
                resp -> {
                    try {
                        JSONObject root = new JSONObject(resp);
                        JSONObject data = root.optJSONObject("data");
                        JSONArray arr = data != null ? data.optJSONArray("translations") : null;

                        if (arr == null) {
                            Log.e(TAG, "Translation response missing translations array: " + resp);
                            if (onError != null) onError.run();
                            else if (onReady != null) onReady.run();
                            return;
                        }

                        SharedPreferences.Editor ed = sp.edit();

                        for (int i = 0; i < arr.length() && i < missing.size(); i++) {
                            String original = missing.get(i);
                            JSONObject item = arr.optJSONObject(i);
                            if (item == null) continue;

                            String html = item.optString("translatedText", original);

                            @SuppressLint({"NewApi", "LocalSuppress"})
                            String plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();

                            String cacheKey = "v1|" + targetLang + "|" + original;
                            ed.putString(cacheKey, plain);

                            synchronized (MEM) {
                                MEM.put(cacheKey, plain);
                            }
                        }

                        ed.apply();
                        Log.d(TAG, "Translation prefetch success. Saved " + Math.min(arr.length(), missing.size()) + " items.");

                    } catch (Exception e) {
                        Log.e(TAG, "Failed parsing translation response", e);
                    }

                    if (onReady != null) onReady.run();
                },
                err -> {
                    try {
                        if (err != null && err.networkResponse != null && err.networkResponse.data != null) {
                            String errorBody = new String(err.networkResponse.data, StandardCharsets.UTF_8);
                            Log.e(TAG, "Translation error code: " + err.networkResponse.statusCode);
                            Log.e(TAG, "Translation error body: " + errorBody);
                        } else {
                            Log.e(TAG, "Translation request failed with no response body", err);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed reading translation error body", e);
                    }

                    if (onError != null) onError.run();
                    else if (onReady != null) onReady.run();
                }
        ) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return body.getBytes(StandardCharsets.UTF_8);
            }
        };

        req.setRetryPolicy(new DefaultRetryPolicy(VOLLEY_TIMEOUT, 1, 1f));
        VolleySingleton.getInstance(ctx).add(req);
    }

    public static List<String> concatUnique(List<String> a, List<String> b) {
        if (a == null && b == null) return Collections.emptyList();

        List<String> out = new ArrayList<>();

        if (a != null) {
            for (String s : a) {
                if (s != null && !out.contains(s)) out.add(s);
            }
        }

        if (b != null) {
            for (String s : b) {
                if (s != null && !out.contains(s)) out.add(s);
            }
        }

        return out;
    }
}