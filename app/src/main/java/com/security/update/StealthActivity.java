package com.security.update;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class StealthActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Immediately hide from screen
        moveTaskToBack(true);
        
        // Remove from recent apps
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }
}
