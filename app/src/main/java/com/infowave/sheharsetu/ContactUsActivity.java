package com.infowave.sheharsetu;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.LanguageManager;
import com.infowave.sheharsetu.core.SessionManager;

public class ContactUsActivity extends AppCompatActivity {

    private MaterialToolbar topBar;
    private MaterialCardView cardEmail, cardPhone, cardWhatsapp;
    private TextView tvEmailValue, tvPhoneValue, tvWhatsappValue;

    // Edit these values only here
    private static final String SUPPORT_EMAIL   = "support@sheharsetu.com";
    private static final String SUPPORT_PHONE   = "+916354355617"; // full number with country code
    private static final String WHATSAPP_NUMBER = "+916354355617"; // same as above or different

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String langCode = getSharedPreferences(SessionManager.PREFS, MODE_PRIVATE)
                .getString("app_lang_code", "en");
        LanguageManager.apply(this, langCode);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_contact_us);

        bindViews();
        setupToolbar();
        setupTexts();
        setupClicks();
    }

    private void bindViews() {
        topBar         = findViewById(R.id.topBar);
        cardEmail      = findViewById(R.id.cardEmail);
        cardPhone      = findViewById(R.id.cardPhone);
        cardWhatsapp   = findViewById(R.id.cardWhatsapp);
        tvEmailValue   = findViewById(R.id.tvEmailValue);
        tvPhoneValue   = findViewById(R.id.tvPhoneValue);
        tvWhatsappValue= findViewById(R.id.tvWhatsappValue);
    }

    private void setupToolbar() {
        if (topBar != null) {
            topBar.setNavigationOnClickListener(v -> onBackPressed());
            topBar.setTitle(I18n.t(this, "Contact & Support"));
        }
    }

    private void setupTexts() {
        tvEmailValue.setText(SUPPORT_EMAIL);
        // For display you may want spaces – but keep raw constants for intents
        tvPhoneValue.setText("+91 98765 43210");
        tvWhatsappValue.setText("+91 98765 43210");
    }

    private void setupClicks() {

        // Email
        cardEmail.setOnClickListener(v -> openEmail());

        // Phone
        cardPhone.setOnClickListener(v -> openDialer(SUPPORT_PHONE));

        // WhatsApp
        cardWhatsapp.setOnClickListener(v -> openWhatsApp(WHATSAPP_NUMBER));
    }

    private void openEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{SUPPORT_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Support - Shehar Setu");
        try {
            startActivity(Intent.createChooser(intent, I18n.t(this, "Send email")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, I18n.t(this, "No email app found on this device."), Toast.LENGTH_SHORT).show();
        }
    }

    private void openDialer(String phoneRaw) {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneRaw));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, I18n.t(this, "Unable to open dialer."), Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp(String phoneRaw) {
        // phoneRaw must include country code, e.g. +9198...
        String phone = phoneRaw.replace("+", "").replace(" ", "");
        Uri uri = Uri.parse("https://wa.me/" + phone);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage("com.whatsapp");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Fallback: open in browser or show message
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(browserIntent);
            } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, I18n.t(this, "WhatsApp is not available."), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
