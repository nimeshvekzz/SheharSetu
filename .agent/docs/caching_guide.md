# Caching Guide - SheharSetu

## The Problem

When you update data on the server (like editing a listing), the app might still show old data. This happens because the app saves (caches) data locally to load faster next time.

---

## Where Caching Happens

### 1. API Responses (Volley)
When the app makes an API call, Volley saves the response. Next time it might return the saved response instead of asking the server again.

**The Fix:** Add `req.setShouldCache(false)` before every API call that fetches user-specific data.

---

### 2. Images (Glide)
Glide saves images on the phone. If you update an image on the server with the same filename, the old image still shows.

**The Fix:** Add a timestamp to image URLs like `?t=123456` so Glide treats it as a new image.

---

### 3. Server Responses (PHP)
The server itself can tell browsers/apps to cache responses.

**The Fix:** Add no-cache headers to all PHP APIs that return user data:
- `Cache-Control: no-cache`
- `Pragma: no-cache`
- `Expires: 0`

---

## Smart Caching for Categories & Other Semi-Static Data

Categories, subcategories, and similar data don't change often. You don't want to:
- Always fetch from server (wastes data, slow)
- Always use cached data (misses updates)

### Best Approach: Refresh on App Start

1. **Save categories locally** using SharedPreferences or a small database
2. **On every app launch**, fetch fresh categories from server in background
3. **Use local data immediately** for fast UI
4. **Replace with fresh data** when server responds

### How It Works:

```
User opens app
    ↓
Show categories from local storage (instant)
    ↓
Meanwhile, call API to get latest categories
    ↓
If new data is different → Update local storage + refresh UI
```

### Where to Implement:
- `CategorySelectActivity.java` - Load from local, then refresh from server
- `MainActivity.java` - Pre-fetch categories when app starts

### Bonus: Version Tracking

Add a `version` or `last_updated` field to your categories API response:
```json
{
  "version": "2026-02-02",
  "categories": [...]
}
```

App saves this version. On next call, compare versions:
- Same version → No need to update UI
- Different version → Refresh everything

---

## Files to Implement Caching Fixes

### PHP API Files (add no-cache headers)

| File | Status | Why |
|------|--------|-----|
| `get_user_listings.php` | ✅ Done | User's own listings |
| `get_user_profile.php` | ✅ Done | User profile data |
| `mark_listing_sold.php` | ✅ Done | User action |
| `update_user_profile.php` | ✅ Done | User action |
| `test_user_profile.php` | ❌ Not needed | Test file only |

### Android Activity Files (add setShouldCache)

| File | Status | Why |
|------|--------|-----|
| `ProfileActivity.java` | ✅ Done | User profile + listings |
| `UserInfoActivity.java` | ✅ Done | fetchStates, fetchDistricts |
| `MainActivity.java` | ✅ Done | fetchUserProfile, fetchCategories |
| `CategorySelectActivity.java` | ✅ Done | Already had caching disabled |
| `LanguageSelection.java` | ❌ Not needed | Static data |

---

## Summary: Which Strategy for What Data

| Data Type | Strategy | Why |
|-----------|----------|-----|
| User profile | No cache | Always needs latest |
| My listings | No cache | User just changed it |
| Product detail | No cache | Prices/availability change |
| Categories | Smart cache | Rarely changes, show fast + update in background |
| Languages | Full cache OK | Never changes |
| App settings | Full cache OK | User controls this |

---

## If User Reports Old Data

1. Check if `setShouldCache(false)` is added in the Activity
2. Check if PHP file has no-cache headers
3. Ask user to clear app data (Settings → Apps → SheharSetu → Clear Data)
4. For images: add timestamp parameter or clear Glide cache
