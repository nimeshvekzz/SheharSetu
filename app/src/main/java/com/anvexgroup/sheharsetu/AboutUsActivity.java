package com.anvexgroup.sheharsetu;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.anvexgroup.sheharsetu.Adapter.AboutSectionAdapter;
import com.anvexgroup.sheharsetu.Adapter.I18n;
import com.anvexgroup.sheharsetu.Adapter.LanguageManager;
import com.anvexgroup.sheharsetu.core.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class AboutUsActivity extends AppCompatActivity {

    private MaterialToolbar topBar;
    private RecyclerView recyclerAboutSections;

    private TextView tvPageTitle, tvPageSubtitle, tvHeadingApp, tvSubHeadingApp, tvFooterTitle, tvFooterDesc;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about_us);

        bindViews();
        setupToolbar();
        setupTexts();
        setupRecycler();
    }

    private void bindViews() {
        topBar = findViewById(R.id.topBar);
        recyclerAboutSections = findViewById(R.id.recyclerAboutSections);

        tvPageTitle = findViewById(R.id.tvPageTitle);
        tvPageSubtitle = findViewById(R.id.tvPageSubtitle);
        tvHeadingApp = findViewById(R.id.tvHeadingApp);
        tvSubHeadingApp = findViewById(R.id.tvSubHeadingApp);
        tvFooterTitle = findViewById(R.id.tvFooterTitle);
        tvFooterDesc = findViewById(R.id.tvFooterDesc);
    }

    private void setupToolbar() {
        topBar.setTitle(I18n.t(this, "About Shehar Setu"));
        topBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupTexts() {
        tvPageTitle.setText(I18n.t(this, "A modern local marketplace built for clarity"));
        tvPageSubtitle.setText(I18n.t(this, "Shehar Setu connects people, local businesses, and services through a simple and well-structured experience."));
        tvHeadingApp.setText(I18n.t(this, "Connecting City, Markets and People"));
        tvSubHeadingApp.setText(I18n.t(this, "Shehar Setu is your bridge between local buyers, sellers, and service providers."));
        tvFooterTitle.setText(I18n.t(this, "Built for trust, clarity and local growth"));
        tvFooterDesc.setText(I18n.t(this, "A cleaner interface and structured listing flow help users browse and post with confidence."));
    }

    private void setupRecycler() {
        recyclerAboutSections.setLayoutManager(new LinearLayoutManager(this));
        recyclerAboutSections.setNestedScrollingEnabled(false);

        List<AboutSectionAdapter.SectionItem> items = new ArrayList<>();

        items.add(new AboutSectionAdapter.SectionItem(
                R.drawable.ic_about_img,
                I18n.t(this, "Platform"),
                I18n.t(this, "What is Shehar Setu?"),
                I18n.t(this, "Local marketplace made simple"),
                I18n.t(this, "Shehar Setu is a local marketplace platform designed to simplify buying, selling, and service discovery in your city. From agriculture to services, real estate to daily needs, users can post and explore listings in a structured and easy-to-use format."),
                null
        ));

        items.add(new AboutSectionAdapter.SectionItem(
                R.drawable.ic_about_buyer,
                I18n.t(this, "For Buyers"),
                I18n.t(this, "Discover nearby opportunities"),
                null,
                I18n.t(this, "Buyers can quickly explore nearby products and services with better filtering and more structured details."),
                I18n.t(this,
                        "• Discover nearby products and services\n" +
                                "• Filter by category, subcategory and condition\n" +
                                "• View clear, structured information with localized language support")
        ));

        items.add(new AboutSectionAdapter.SectionItem(
                R.drawable.ic_about_seller,
                I18n.t(this, "For Sellers"),
                I18n.t(this, "Post with confidence"),
                null,
                I18n.t(this, "Sellers and service providers get a guided listing experience with category-based forms and richer details."),
                I18n.t(this,
                        "• Post detailed listings with dynamic forms\n" +
                                "• Highlight price, quantity, condition, location and images\n" +
                                "• Reach relevant local users without complexity")
        ));

        items.add(new AboutSectionAdapter.SectionItem(
                R.drawable.ic_about_secure,
                I18n.t(this, "Vision"),
                I18n.t(this, "Our Vision"),
                I18n.t(this, "Trusted digital bridge for every city"),
                I18n.t(this, "To become the trusted digital bridge of every city by connecting citizens, markets, and services with clarity, transparency, and simplicity."),
                I18n.t(this, "• Structured\n• Local-first\n• Supportive")
        ));

        items.add(new AboutSectionAdapter.SectionItem(
                R.drawable.ic_about_support,
                I18n.t(this, "Support"),
                I18n.t(this, "Support and Feedback"),
                null,
                I18n.t(this, "For any issues, suggestions, or feedback related to Shehar Setu, users can reach out to the development team."),
                I18n.t(this, "• Email: support@sheharsetu.com")
        ));

        recyclerAboutSections.setAdapter(new AboutSectionAdapter(this, items));
    }
}