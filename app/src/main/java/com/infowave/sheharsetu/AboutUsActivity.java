package com.infowave.sheharsetu;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.core.SessionManager;

import java.util.Arrays;
import java.util.List;

public class AboutUsActivity extends AppCompatActivity {

    private MaterialToolbar topBar;

    // Text views
    private TextView tvHeadingApp, tvSubHeadingApp;
    private TextView tvAboutTitle, tvAboutDesc;
    private TextView tvChipMarketplace, tvChipMultiCategory, tvChipDynamicForms;
    private TextView tvBuyersTitle, tvBuyersPoints;
    private TextView tvSellersTitle, tvSellersPoints;
    private TextView tvVisionTitle, tvVisionDesc;
    private TextView tvValueStructured, tvValueLocalFirst, tvValueSupportive;
    private TextView tvSupportTitle, tvSupportDesc, tvSupportEmail;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        // --- EDGE-TO-EDGE AND PERFECT FIT CORE LOGIC ---
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
            windowInsetsController.setAppearanceLightNavigationBars(false);
        }

        setContentView(R.layout.activity_about_us);

        bindViews();
        setupToolbar();
        setupInsets(); // Apply perfect fit insets
        prefetchAndSetupTexts();
    }

    private void setupInsets() {
        android.view.View root = findViewById(R.id.rootAbout);
        android.view.View statusBarBg = findViewById(R.id.viewStatusBarBackground);
        android.view.View navBarBg = findViewById(R.id.viewNavBarBackground);

        if (root != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());

                if (statusBarBg != null) {
                    statusBarBg.getLayoutParams().height = systemBars.top;
                    statusBarBg.requestLayout();
                }

                if (navBarBg != null) {
                    navBarBg.getLayoutParams().height = systemBars.bottom;
                    navBarBg.requestLayout();
                }

                return insets;
            });
        }
    }

    private void bindViews() {
        topBar = findViewById(R.id.topBar);

        tvHeadingApp        = findViewById(R.id.tvHeadingApp);
        tvSubHeadingApp     = findViewById(R.id.tvSubHeadingApp);

        tvAboutTitle        = findViewById(R.id.tvAboutTitle);
        tvAboutDesc         = findViewById(R.id.tvAboutDesc);
        tvChipMarketplace   = findViewById(R.id.tvChipMarketplace);
        tvChipMultiCategory = findViewById(R.id.tvChipMultiCategory);
        tvChipDynamicForms  = findViewById(R.id.tvChipDynamicForms);

        tvBuyersTitle       = findViewById(R.id.tvBuyersTitle);
        tvBuyersPoints      = findViewById(R.id.tvBuyersPoints);
        tvSellersTitle      = findViewById(R.id.tvSellersTitle);
        tvSellersPoints     = findViewById(R.id.tvSellersPoints);

        tvVisionTitle       = findViewById(R.id.tvVisionTitle);
        tvVisionDesc        = findViewById(R.id.tvVisionDesc);
        tvValueStructured   = findViewById(R.id.tvValueStructured);
        tvValueLocalFirst   = findViewById(R.id.tvValueLocalFirst);
        tvValueSupportive   = findViewById(R.id.tvValueSupportive);

        tvSupportTitle      = findViewById(R.id.tvSupportTitle);
        tvSupportDesc       = findViewById(R.id.tvSupportDesc);
        tvSupportEmail      = findViewById(R.id.tvSupportEmail);
    }

    private void setupToolbar() {
        if (topBar != null) {
            topBar.setNavigationOnClickListener(v -> onBackPressed());
            topBar.setTitle(I18n.t(this, "About Shehar Setu"));
        }
    }

    private void prefetchAndSetupTexts() {
        List<String> keys = Arrays.asList(
                "About Shehar Setu",
                "Connecting City, Markets and People",
                "Shehar Setu is your bridge between local buyers, sellers and service providers.",
                "What is Shehar Setu?",
                "Shehar Setu is a local marketplace platform designed to simplify buying, " +
                        "selling and service discovery in your city. From agriculture to services, " +
                        "real estate to daily needs, citizens can post and explore listings in a " +
                        "structured, easy-to-use format.",
                "Local Marketplace", "Multi-category", "Dynamic Forms",
                "For Buyers",
                "- Discover nearby products and services.\n" +
                        "- Filter by category, subcategory and condition.\n" +
                        "- View clear, structured information with localized language support.",
                "For Sellers and Service Providers",
                "- Post detailed listings with category-specific dynamic forms.\n" +
                        "- Highlight condition, price, quantity, location and images.\n" +
                        "- Reach relevant local users without complexity.",
                "Our Vision",
                "To become the trusted digital bridge of every city – connecting citizens, " +
                        "markets and services with clarity, transparency and simplicity.",
                "Structured", "Local-first", "Supportive",
                "Support and Feedback",
                "For any issues, suggestions or feedback related to Shehar Setu, you can " +
                        "reach out to the development team.",
                "Email: support@sheharsetu.com"
        );
        I18n.prefetch(this, keys, () -> {
            setupToolbar();
            setupTexts();
        }, () -> setupTexts());
    }

    /**
     * All visible text for the About screen is defined here,
     * wrapped in I18n.t() for runtime translation.
     */
    private void setupTexts() {

        // Hero section
        tvHeadingApp.setText(I18n.t(this, "Connecting City, Markets and People"));
        tvSubHeadingApp.setText(I18n.t(this,
                "Shehar Setu is your bridge between local buyers, sellers and service providers."
        ));

        // About section
        tvAboutTitle.setText(I18n.t(this, "What is Shehar Setu?"));
        tvAboutDesc.setText(I18n.t(this,
                "Shehar Setu is a local marketplace platform designed to simplify buying, " +
                        "selling and service discovery in your city. From agriculture to services, " +
                        "real estate to daily needs, citizens can post and explore listings in a " +
                        "structured, easy-to-use format."
        ));
        tvChipMarketplace.setText(I18n.t(this, "Local Marketplace"));
        tvChipMultiCategory.setText(I18n.t(this, "Multi-category"));
        tvChipDynamicForms.setText(I18n.t(this, "Dynamic Forms"));

        // Buyers / Sellers
        tvBuyersTitle.setText(I18n.t(this, "For Buyers"));
        tvBuyersPoints.setText(I18n.t(this,
                "- Discover nearby products and services.\n" +
                        "- Filter by category, subcategory and condition.\n" +
                        "- View clear, structured information with localized language support."
        ));

        tvSellersTitle.setText(I18n.t(this, "For Sellers and Service Providers"));
        tvSellersPoints.setText(I18n.t(this,
                "- Post detailed listings with category-specific dynamic forms.\n" +
                        "- Highlight condition, price, quantity, location and images.\n" +
                        "- Reach relevant local users without complexity."
        ));

        // Vision & values
        tvVisionTitle.setText(I18n.t(this, "Our Vision"));
        tvVisionDesc.setText(I18n.t(this,
                "To become the trusted digital bridge of every city – connecting citizens, " +
                        "markets and services with clarity, transparency and simplicity."
        ));
        tvValueStructured.setText(I18n.t(this, "Structured"));
        tvValueLocalFirst.setText(I18n.t(this, "Local-first"));
        tvValueSupportive.setText(I18n.t(this, "Supportive"));

        // Support
        tvSupportTitle.setText(I18n.t(this, "Support and Feedback"));
        tvSupportDesc.setText(I18n.t(this,
                "For any issues, suggestions or feedback related to Shehar Setu, you can " +
                        "reach out to the development team."
        ));
        tvSupportEmail.setText("Email: support@sheharsetu.com"); // keep email raw
    }
}
