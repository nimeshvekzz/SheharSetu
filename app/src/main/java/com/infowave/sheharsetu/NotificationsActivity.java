package com.infowave.sheharsetu;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.Adapter.I18n;
import com.infowave.sheharsetu.Adapter.NotificationAdapter;
import com.infowave.sheharsetu.model.NotificationModel;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private LinearLayout layoutEmptyState;
    private NotificationAdapter adapter;
    private final List<NotificationModel> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // --- EDGE-TO-EDGE AND PERFECT FIT CORE LOGIC ---
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
            windowInsetsController.setAppearanceLightNavigationBars(false);
        }
        
        setContentView(R.layout.activity_notifications);

        bindViews();
        setupToolbar();
        setupRecyclerView();
        setupInsets(); // Apply the perfect fit insets
        loadMockNotifications();
    }

    private void setupInsets() {
        View root = findViewById(R.id.rootNotifications);
        View statusBarBg = findViewById(R.id.viewStatusBarBackground);
        View navBarBg = findViewById(R.id.viewNavBarBackground);

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
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.topBar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(I18n.t(this, "Notifications"));
            }
        }
    }

    private void setupRecyclerView() {
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this, notifications);
        rvNotifications.setAdapter(adapter);
    }

    private void loadMockNotifications() {
        // Mock data to demonstrate the "perfect" UI
        notifications.add(new NotificationModel("1", 
            "Welcome to Shehar Setu!", 
            "Thank you for joining our community. Start exploring listings and connecting with local sellers today!", 
            "2 hours ago", false, "INFO"));
        
        notifications.add(new NotificationModel("2", 
            "Ad Approved: Honda Activa", 
            "Congratulations! Your listing for 'Honda Activa 2021' has been verified and is now visible to all users.", 
            "5 hours ago", false, "SUCCESS"));
        
        notifications.add(new NotificationModel("3", 
            "Profile Verified", 
            "Your account security has been enhanced with mobile verification. Thank you for keeping Shehar Setu safe.", 
            "Yesterday", true, "SUCCESS"));
        
        notifications.add(new NotificationModel("4", 
            "Security Alert", 
            "A new login was detected from a Windows device in your city. If this wasn't you, please contact support immediately.", 
            "2 days ago", true, "WARNING"));

        notifications.add(new NotificationModel("5", 
            "Account Tip", 
            "Adding more than 3 clear photos to your ad can increase buyer interest by up to 50%!", 
            "3 days ago", true, "INFO"));

        if (notifications.isEmpty()) {
            rvNotifications.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
    }
}
