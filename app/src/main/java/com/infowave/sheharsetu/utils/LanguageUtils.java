package com.infowave.sheharsetu.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import com.infowave.sheharsetu.Adapter.I18n;

/**
 * Utility class for language management and translation
 */
public class LanguageUtils {
    
    private static final String PREFS_NAME = "sheharsetu_prefs";
    private static final String KEY_LANGUAGE = "app_lang_code";
    
    /**
     * Get current language code
     */
    public static String getCurrentLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, "en");
    }
    
    /**
     * Set current language
     */
    public static void setCurrentLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LANGUAGE, languageCode);
        editor.apply();
    }
    
    /**
     * Check if current language is English
     */
    public static boolean isEnglish(Context context) {
        return "en".equals(getCurrentLanguage(context));
    }
    
    /**
     * Apply translation to TextView if language is not English
     */
    public static void translateTextView(Context context, TextView textView) {
        if (!isEnglish(context)) {
            I18n.translateAndApplyText(textView, context);
        }
    }
    
    /**
     * Get translated text
     */
    public static String getTranslatedText(Context context, String text) {
        if (isEnglish(context)) {
            return text;
        }
        return I18n.t(context, text);
    }
    
    /**
     * Get language display name
     */
    public static String getLanguageDisplayName(String languageCode) {
        switch (languageCode) {
            case "hi": return "हिन्दी";
            case "gu": return "ગુજરાતી";
            case "mr": return "मराठी";
            case "pa": return "ਪੰਜਾਬੀ";
            case "bn": return "বাংলা";
            case "ta": return "தமிழ்";
            case "te": return "తెలుగు";
            case "ml": return "മലയാളം";
            case "kn": return "ಕನ್ನಡ";
            case "or": return "ଓଡିଆ";
            case "ur": return "اردو";
            case "as": return "অসমীয়া";
            case "mai": return "मैथिली";
            default: return "English";
        }
    }
    
    /**
     * Check if language needs RTL layout
     */
    public static boolean isRTLLanguage(String languageCode) {
        return "ur".equals(languageCode) || "ar".equals(languageCode);
    }
}
