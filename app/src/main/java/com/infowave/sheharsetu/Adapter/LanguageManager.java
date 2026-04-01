package com.infowave.sheharsetu.Adapter;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LanguageManager {
    public static void apply(Activity activity, String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration(activity.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        } else {
            //noinspection deprecation
            config.locale = locale;
        }
        activity.getResources().updateConfiguration(
                config,
                activity.getResources().getDisplayMetrics()
        );
    }
}
