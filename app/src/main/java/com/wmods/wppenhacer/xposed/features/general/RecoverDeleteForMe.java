package com.wmods.wppenhacer.xposed.features.general;

import android.content.Context;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;
import com.wmods.wppenhacer.xposed.core.db.DeletedMessage;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class RecoverDeleteForMe extends Feature {

    public RecoverDeleteForMe(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        try {
            Class<?> cms = Unobfuscator.loadCoreMessageStore(classLoader);

            // A06 is the static "Delete for Me" method: (X.0Ag, Collection, int)
            Method targetMethod = null;
            for (Method m : cms.getDeclaredMethods()) {
                if (!m.getName().equals("A06")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && Collection.class.isAssignableFrom(p[1]) && p[2] == int.class) {
                    targetMethod = m;
                    break;
                }
            }

            if (targetMethod == null) {
                XposedBridge.log("WAE: RecoverDeleteForMe: A06 not found");
                return;
            }

            XposedBridge.hookMethod(targetMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.log("WAE: A06 Fired!");
                        Collection<?> msgs = (Collection<?>) param.args[1];
                        if (msgs == null) {
                            XposedBridge.log("WAE: msgs is null");
                            return;
                        }
                        if (msgs.isEmpty()) {
                            XposedBridge.log("WAE: msgs is empty");
                            return;
                        }
                        Context ctx = Utils.getApplication();
                        if (ctx == null) {
                            XposedBridge.log("WAE: Context is null");
                            return;
                        }
                        // DelMessageStore store = DelMessageStore.getInstance(ctx); // No longer needed for insertion
                        for (Object msg : msgs) {
                            try {
                                saveOne(ctx, msg);
                            } catch (Throwable t) {
                                XposedBridge.log("WAE: RecoverDeleteForMe saveOne: " + t.getMessage());
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("WAE: RecoverDeleteForMe hook: " + t.getMessage());
                    }
                }
            });
            XposedBridge.log("WAE: RecoverDeleteForMe hooked A06 OK");

        } catch (Exception e) {
            XposedBridge.log("WAE: RecoverDeleteForMe init: " + e.getMessage());
        }
    }

    private void saveOne(Context context, Object msg) throws Exception {
        if (msg == null) return;
        Class<?> msgClass = msg.getClass();

        // 1. Find Key Field (Scan for first NON-NULL field of Key type)
        Object key = null;
        if (FMessageWpp.Key.TYPE != null) {
            key = getFirstNonNullFieldByType(msg, FMessageWpp.Key.TYPE);
        }
        
        // Fallback: search by name "key" if type search failed or key type unknown
        if (key == null) {
            Field keyField = findField(msgClass, "key");
             if (keyField != null) key = keyField.get(msg);
        }

        if (key == null) return;
        
        // 2. Extract Message ID
        String msgId = getStr(key, "id");
        if (msgId == null) msgId = getStr(key, "A01"); // Obfuscated ID
        if (msgId == null) {
             XposedBridge.log("WAE: msgId not found on Key " + key.getClass().getName());
             return;
        }

        // 3. Extract RemoteJid / ChatJid
        String chatJid = null;
        Object jidObj = getObj(key, "remoteJid");
        if (jidObj == null) jidObj = getObj(key, "chatJid");
        if (jidObj == null) jidObj = getObj(key, "A00"); // Obfuscated RemoteJid
        
        if (jidObj != null) chatJid = jidObj.toString();
        
        // Fix: Ensure JID isn't "false" or "true" due to bad reflection
        if (chatJid != null && (chatJid.equalsIgnoreCase("false") || chatJid.equalsIgnoreCase("true"))) {
            chatJid = null;
        }

        // 4. Extract isFromMe
        boolean fromMe = false;
        Field fmField = findField(key.getClass(), "fromMe");
        if (fmField == null) fmField = findField(key.getClass(), "isFromMe");
        if (fmField == null) fmField = findField(key.getClass(), "A02"); // Obfuscated fromMe
        
        if (fmField != null) {
            Object v = fmField.get(key);
            if (v instanceof Boolean) fromMe = (Boolean) v;
        }

        // 5. Extract Text Body
        String text = getStr(msg, "text");
        if (text == null) text = getStr(msg, "body");
        if (text == null) text = getStr(msg, "A0Q");
        
        // Scan for all strings to find the text
        if (text == null) {
             String bestCandidate = null;
             Class<?> cls = msgClass;
             while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType().equals(String.class)) {
                        f.setAccessible(true);
                        try {
                            String val = (String) f.get(msg);
                            if (val != null && !val.isEmpty()) {
                                if (bestCandidate == null || val.length() > bestCandidate.length()) {
                                    if (val.length() > 1) bestCandidate = val;
                                }
                            }
                        } catch (IllegalAccessException e) { }
                    }
                }
                cls = cls.getSuperclass();
            }
            if (bestCandidate != null) {
                text = bestCandidate;
            }
        }

        // 6. Media Type
        int mediaType = -1;
        Field mtf = findField(msgClass, "mediaType");
        if (mtf == null) mtf = findField(msgClass, "media_wa_type");
        if (mtf != null) {
            try { mediaType = mtf.getInt(msg); } catch (Exception ignored) {}
        }

        String senderJid = fromMe ? "Me" : chatJid;
        Object participant = getObj(msg, "participant");
        if (participant == null) participant = getObj(msg, "senderJid");
        if (participant == null) participant = getObj(msg, "A0b"); 
        
        if (participant != null) {
             String val = participant.toString();
             if (!val.equalsIgnoreCase("false") && !val.equalsIgnoreCase("true")) {
                 senderJid = val;
             }
        }

        // 5. Extract Text Content
        String textContent = null;
        if (msgObj != null) {
            textContent = findLongestString(msgObj);
        }

        // 6. Extract Media (if any)
        int mediaType = -1;
        String mediaCaption = null;
        String mediaPath = null;
        
        // Original media extraction logic
        Field mtf = findField(msgClass, "mediaType");
        if (mtf == null) mtf = findField(msgClass, "media_wa_type");
        if (mtf != null) {
            try { mediaType = mtf.getInt(msgObj); } catch (Exception ignored) {}
        }

        Object mf = getObj(msgObj, "mediaData");
        if (mf == null) mf = getObj(msgObj, "mediaFile");
        if (mf instanceof File && ((File) mf).exists()) {
            mediaPath = ((File) mf).getAbsolutePath();
        }

        String caption = getStr(msgObj, "caption");
        if (caption == null) caption = getStr(msgObj, "mediaCaption");
        if (caption == null) caption = getStr(msgObj, "A03"); 
        mediaCaption = caption; // Assign to mediaCaption

        long timestamp = System.currentTimeMillis(); // Assuming current time for deleted message

        // --- NEW: Contact Name Resolution ---
        String contactName = null;
        try {
            if (chatJid != null) {
                if (chatJid.contains("@g.us")) {
                    // Group Chat: Try to get Subject
                    // We can try to find the "subject" or "name" field in the chat object if available, 
                    // or use a utility if we can find one. 
                    // For now, let's try a common trick: query the Contacts database for the Group JID (WaEnhancer often syncs groups)
                    // OR better: use the same ContactHelper logic but inside the hook context
                    contactName = getContactName(context, chatJid);
                } else {
                    // Individual Chat: Resolve from ContactsContract
                    contactName = getContactName(context, chatJid);
                }
            }
            if (contactName == null && senderJid != null && chatJid.contains("@g.us")) {
                 // If in group and sender is known, maybe we want sender name? 
                 // But UI wants Title to be Group Name. 
                 // We will leave contactName as null if Group Name not found, 
                 // and let UI handle Sender Name for the bubble separately (or we can save sender name too?)
                 // For now, let's stick to Chat Title.
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Save to Database
        DeletedMessage deletedMessage = new DeletedMessage(
                0, keyId, chatJid, senderJid, timestamp, mediaType, textContent, mediaPath, mediaCaption, fromMe, contactName
        );
        
        saveToDatabase(context, deletedMessage);
    }
    
    private String getContactName(Context context, String jid) {
        if (jid == null) return null;
        String phoneNumber = jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
        if (phoneNumber.contains("@")) phoneNumber = phoneNumber.split("@")[0];

        try {
            android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber));
            String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME};

            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveToDatabase(Context context, DeletedMessage message) {
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put("key_id", message.getKeyId());
            values.put("chat_jid", message.getChatJid());
            values.put("sender_jid", message.getSenderJid());
            values.put("timestamp", message.getTimestamp());
            values.put("media_type", message.getMediaType());
            values.put("text_content", message.getTextContent());
            values.put("media_path", message.getMediaPath());
            values.put("media_caption", message.getMediaCaption());
            values.put("is_from_me", message.isFromMe() ? 1 : 0);
            values.put("contact_name", message.getContactName()); // New field

            android.net.Uri uri = android.net.Uri.parse("content://com.wmods.wppenhacer.provider/deleted_messages");
            context.getContentResolver().insert(uri, values);
            XposedBridge.log("WAE: RecoverDeleteForMe saved via Provider: id=" + message.getKeyId() + " text=" + message.getTextContent());
        } catch (Exception e) {
            XposedBridge.log("WAE: Failed to insert to provider: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Scans hierarchy for ALL fields of type, returns first non-null value
    private Object getFirstNonNullFieldByType(Object target, Class<?> type) {
        if (target == null || type == null) return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(target);
                        if (val != null) return val;
                    } catch (IllegalAccessException e) {
                        // ignore
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private String getFirstNonNullStringField(Object target) {
        if (target == null) return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(String.class)) {
                    f.setAccessible(true);
                    try {
                        String val = (String) f.get(target);
                        if (val != null && !val.isEmpty()) return val;
                    } catch (IllegalAccessException e) { }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private Field findFieldByType(Class<?> cls, Class<?> type) {
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    return f;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private Field findField(Class<?> cls, String name) {
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private String getStr(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return null;
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Exception e) { return null; }
    }

    private Object getObj(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return null;
            return f.get(obj);
        } catch (Exception e) { return null; }
    }

    @Override
    public String getPluginName() {
        return "Recover Delete For Me";
    }

    public static void restoreMessage(android.content.Context context, DeletedMessage message) {
        try {
            if (message.getTextContent() != null && !message.getTextContent().isEmpty()) {
                android.widget.Toast.makeText(context, "Message: " + message.getTextContent(), android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(context, "Media restore not supported yet", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: Restore failed: " + e.getMessage());
        }
    }
}
