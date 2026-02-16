package com.infowave.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
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


    private static final String GOOGLE_TRANSLATE_API_KEY = "AIzaSyCkUxQSJ1jNt0q_CcugieFl5vezsNAUxe0";
    private static final String G_URL = "https://translation.googleapis.com/language/translate/v2";

    private static final String SP_NAME = "i18n_cache_v1";
    private static final int VOLLEY_TIMEOUT = 12000;

    private static final int MEM_CACHE_CAP = 500;
    private static final Map<String, String> MEM = new LinkedHashMap<String, String>(MEM_CACHE_CAP, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > MEM_CACHE_CAP; }
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
            String v = MEM.get(memKey);
            if (v != null) return v;
        }

        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String v = sp.getString(memKey, null);
        if (v == null) return key;

        synchronized (MEM) { MEM.put(memKey, v); }
        return v;
    }

    public static void translateAndApplyText(TextView tv, Context ctx) {
        if (tv == null) return;
        CharSequence now = tv.getText();
        String tr = t(ctx, now == null ? "" : now.toString());
        tv.setText(tr);
    }

    public static void translateAndApplyHint(com.google.android.material.textfield.TextInputLayout til, Context ctx) {
        if (til == null) return;
        CharSequence h = til.getHint();
        String base = h == null ? "" : h.toString();
        til.setHint(t(ctx, base));
    }

    public static void prefetch(Context ctx, List<String> keys, Runnable onReady) {
        prefetch(ctx, keys, onReady, null);
    }

    public static void prefetch(Context ctx, List<String> keys, Runnable onReady, Runnable onError) {
        String l = lang(ctx);
        if (keys == null || keys.isEmpty() || "en".equalsIgnoreCase(l)) {
            if (onReady != null) onReady.run();
            return;
        }

        // Normalize & de-duplicate
        List<String> unique = new ArrayList<>();
        for (String k : keys) {
            if (k == null) continue;
            k = k.trim();
            if (k.isEmpty()) continue;
            if (!unique.contains(k)) unique.add(k);
        }
        if (unique.isEmpty()) { if (onReady != null) onReady.run(); return; }

        // Check which ones are missing from cache
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        List<String> missing = new ArrayList<>();
        for (String k : unique) {
            String memKey = "v1|" + l + "|" + k;
            boolean have;
            synchronized (MEM) { have = MEM.containsKey(memKey); }
            if (!have && sp.getString(memKey, null) == null) {
                missing.add(k);
            }
        }
        if (missing.isEmpty()) { if (onReady != null) onReady.run(); return; }

        final String body;
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : missing) {
                if (sb.length() > 0) sb.append("&");
                sb.append("q=").append(URLEncoder.encode(s, "UTF-8"));
            }
            sb.append("&target=").append(URLEncoder.encode(l, "UTF-8"));
            sb.append("&format=text");
            sb.append("&key=").append(URLEncoder.encode(GOOGLE_TRANSLATE_API_KEY, "UTF-8"));
            body = sb.toString();
        } catch (Exception e) {
            if (onError != null) onError.run(); else if (onReady != null) onReady.run();
            return;
        }

        StringRequest req = new StringRequest(
                Request.Method.POST, G_URL,
                resp -> {
                    try {
                        JSONObject o = new JSONObject(resp);
                        JSONObject data = o.optJSONObject("data");
                        JSONArray arr = data != null ? data.optJSONArray("translations") : null;
                        if (arr == null) { if (onError != null) onError.run(); else if (onReady != null) onReady.run(); return; }

                        SharedPreferences.Editor ed = sp.edit();
                        for (int i = 0; i < arr.length() && i < missing.size(); i++) {
                            String en = missing.get(i);
                            JSONObject ti = arr.optJSONObject(i);
                            if (ti == null) continue;
                            String html = ti.optString("translatedText", en);
                            @SuppressLint({"NewApi", "LocalSuppress"}) String plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();

                            String cacheKey = "v1|" + l + "|" + en;
                            ed.putString(cacheKey, plain);
                            synchronized (MEM) { MEM.put(cacheKey, plain); }
                        }
                        ed.apply();
                    } catch (Exception ignored) {}
                    if (onReady != null) onReady.run();
                },
                err -> { if (onError != null) onError.run(); else if (onReady != null) onReady.run(); }
        ) {
            @Override public String getBodyContentType() { return "application/x-www-form-urlencoded; charset=UTF-8"; }
            @Override public byte[] getBody() throws AuthFailureError { return body.getBytes(StandardCharsets.UTF_8); }
        };
        req.setRetryPolicy(new DefaultRetryPolicy(VOLLEY_TIMEOUT, 1, 1f));
        VolleySingleton.getInstance(ctx).add(req);
    }

    public static List<String> concatUnique(List<String> a, List<String> b) {
        if (a == null && b == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        if (a != null) for (String s : a) if (s != null && !out.contains(s)) out.add(s);
        if (b != null) for (String s : b) if (s != null && !out.contains(s)) out.add(s);
        return out;
    }
}
