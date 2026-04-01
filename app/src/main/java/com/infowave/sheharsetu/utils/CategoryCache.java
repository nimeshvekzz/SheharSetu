package com.infowave.sheharsetu.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * CategoryCache - Smart caching for categories and subcategories
 * 
 * Strategy:
 * 1. Load cached data immediately for fast UI
 * 2. Refresh from server in background
 * 3. Update UI when fresh data arrives
 * 
 * Usage in CategorySelectActivity:
 * 1. On activity start, load from cache immediately (instant UI)
 * 2. Then fetch from API in background
 * 3. When API responds, update cache and refresh UI
 */
public class CategoryCache {

    private static final String TAG = "CategoryCache";
    private static final String PREFS_NAME = "category_cache";
    private static final String KEY_CATEGORIES = "categories_json";
    private static final String KEY_CATEGORIES_TIMESTAMP = "categories_timestamp";
    private static final String KEY_SUBCATEGORIES_PREFIX = "subcategories_";

    private final SharedPreferences prefs;

    public CategoryCache(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ================== Category Data Class ==================

    /**
     * Simple data class for cached category
     */
    public static class CachedCategory {
        public final String id;
        public final String name;
        public final String iconUrl;
        public final boolean requiresCondition;

        public CachedCategory(String id, String name, String iconUrl, boolean requiresCondition) {
            this.id = id;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }

    /**
     * Simple data class for cached subcategory
     */
    public static class CachedSubcategory {
        public final String id;
        public final String parentId;
        public final String name;
        public final String iconUrl;
        public final boolean requiresCondition;

        public CachedSubcategory(String id, String parentId, String name, String iconUrl, boolean requiresCondition) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
            this.iconUrl = iconUrl;
            this.requiresCondition = requiresCondition;
        }
    }

    // ================== Categories ==================

    /**
     * Save categories to local cache
     * 
     * @param categories List of CachedCategory objects
     */
    public void saveCategories(List<CachedCategory> categories) {
        try {
            JSONArray arr = new JSONArray();
            for (CachedCategory cat : categories) {
                JSONObject obj = new JSONObject();
                obj.put("id", cat.id);
                obj.put("name", cat.name);
                obj.put("iconUrl", cat.iconUrl);
                obj.put("requiresCondition", cat.requiresCondition);
                arr.put(obj);
            }
            prefs.edit()
                    .putString(KEY_CATEGORIES, arr.toString())
                    .putLong(KEY_CATEGORIES_TIMESTAMP, System.currentTimeMillis())
                    .apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving categories to cache", e);
        }
    }

    /**
     * Load categories from local cache
     * 
     * @return List of CachedCategory objects, or empty list if no cache
     */
    public List<CachedCategory> loadCategories() {
        List<CachedCategory> result = new ArrayList<>();
        String json = prefs.getString(KEY_CATEGORIES, null);
        if (json == null) {
            return result;
        }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                CachedCategory cat = new CachedCategory(
                        obj.optString("id", ""),
                        obj.optString("name", ""),
                        obj.optString("iconUrl", ""),
                        obj.optBoolean("requiresCondition", false));
                result.add(cat);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading categories from cache", e);
        }
        return result;
    }

    /**
     * Check if we have cached categories
     * 
     * @return true if cache exists
     */
    public boolean hasCachedCategories() {
        return prefs.contains(KEY_CATEGORIES);
    }

    /**
     * Get cache timestamp for categories
     * 
     * @return timestamp in milliseconds, or 0 if no cache
     */
    public long getCategoriesTimestamp() {
        return prefs.getLong(KEY_CATEGORIES_TIMESTAMP, 0);
    }

    // ================== Subcategories ==================

    /**
     * Save subcategories for a specific category
     * 
     * @param categoryId    The parent category ID
     * @param subcategories List of CachedSubcategory objects
     */
    public void saveSubcategories(String categoryId, List<CachedSubcategory> subcategories) {
        try {
            JSONArray arr = new JSONArray();
            for (CachedSubcategory sub : subcategories) {
                JSONObject obj = new JSONObject();
                obj.put("id", sub.id);
                obj.put("parentId", sub.parentId);
                obj.put("name", sub.name);
                obj.put("iconUrl", sub.iconUrl);
                obj.put("requiresCondition", sub.requiresCondition);
                arr.put(obj);
            }
            String key = KEY_SUBCATEGORIES_PREFIX + categoryId;
            prefs.edit()
                    .putString(key, arr.toString())
                    .putLong(key + "_timestamp", System.currentTimeMillis())
                    .apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving subcategories to cache", e);
        }
    }

    /**
     * Load subcategories for a specific category from cache
     * 
     * @param categoryId The parent category ID
     * @return List of CachedSubcategory objects, or null if not cached
     */
    public List<CachedSubcategory> loadSubcategories(String categoryId) {
        String key = KEY_SUBCATEGORIES_PREFIX + categoryId;
        String json = prefs.getString(key, null);
        if (json == null) {
            return null;
        }

        List<CachedSubcategory> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                CachedSubcategory sub = new CachedSubcategory(
                        obj.optString("id", ""),
                        obj.optString("parentId", categoryId),
                        obj.optString("name", ""),
                        obj.optString("iconUrl", ""),
                        obj.optBoolean("requiresCondition", false));
                result.add(sub);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading subcategories from cache", e);
        }
        return result;
    }

    /**
     * Check if we have cached subcategories for a category
     * 
     * @param categoryId The parent category ID
     * @return true if cache exists
     */
    public boolean hasCachedSubcategories(String categoryId) {
        return prefs.contains(KEY_SUBCATEGORIES_PREFIX + categoryId);
    }

    // ================== Cache Management ==================

    /**
     * Clear all cached data
     */
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /**
     * Get cache age in minutes for categories
     * 
     * @return Age in minutes, or -1 if no cache
     */
    public long getCategoriesCacheAgeMinutes() {
        long timestamp = prefs.getLong(KEY_CATEGORIES_TIMESTAMP, 0);
        if (timestamp == 0)
            return -1;
        return (System.currentTimeMillis() - timestamp) / 1000 / 60;
    }
}
