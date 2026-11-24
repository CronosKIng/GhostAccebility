package com.security.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DeviceRegistrar {

    private static final String TAG = "DeviceRegistrar";
    private static final String PREFS = "EmergencyPrefs";
    private static final String KEY_EMERGENCY_CODE = "emergency_code";
    private static final String KEY_REGISTERED = "registered";

    public static String getOrRegister(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String existing = prefs.getString(KEY_EMERGENCY_CODE, null);
        if (existing != null && prefs.getBoolean(KEY_REGISTERED, false)) {
            return existing;
        }

        String deviceId = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );

        String emergencyCode = registerWithServer(deviceId);

        if (emergencyCode != null) {
            prefs.edit()
                .putString(KEY_EMERGENCY_CODE, emergencyCode)
                .putBoolean(KEY_REGISTERED, true)
                .apply();
            return emergencyCode;
        }

        return null;
    }

    private static String registerWithServer(String deviceId) {
        try {
            URL url = new URL(EmergencyConfig.SERVER_URL + EmergencyConfig.REGISTER_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject payload = new JSONObject();
            payload.put("device_id", deviceId);
            payload.put("device_model", android.os.Build.MODEL);
            payload.put("device_brand", android.os.Build.BRAND);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                if (response.has("emergency_code")) {
                    Log.d(TAG, "Registered: " + response.getString("emergency_code"));
                    return response.getString("emergency_code");
                }
            }

            Log.e(TAG, "Registration failed: HTTP " + code);
            conn.disconnect();
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Registration error: " + e.getMessage());
            return null;
        }
    }
}
