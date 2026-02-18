package com.wmods.wppenhacer.xposed.features.general;

import android.content.Context;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp;
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

            // Dynamic method search based on signature: (Any, Collection, int)
            Method targetMethod = null;
            for (Method m : cms.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && Collection.class.isAssignableFrom(p[1]) && p[2] == int.class) {
                    targetMethod = m;
                    XposedBridge.log("WAE: Found potential DeleteForMe method: " + m.getName());
                    break; // Assuming only one method matches this signature in CoreMessageStore
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
                        // DelMessageStore store = DelMessageStore.getInstance(ctx); // No longer needed
                        // for insertion
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
        if (msg == null)
            return;
        Class<?> msgClass = msg.getClass();

        // 1. Find Key Field
        Object key = null;
        if (FMessageWpp.Key.TYPE != null) {
            key = getFirstNonNullFieldByType(msg, FMessageWpp.Key.TYPE);
        }
        if (key == null) {
            Field keyField = findField(msgClass, "key");
            if (keyField != null)
                key = keyField.get(msg);
        }
        if (key == null)
            return;

        // 2. Extract Message ID (Key ID)
        String keyId = getStr(key, "id");
        if (keyId == null)
            keyId = getStr(key, "A01");
        if (keyId == null)
            return;

        // 3. Extract RemoteJid / ChatJid
        String chatJid = null;
        Object jidObj = getObj(key, "remoteJid");
        if (jidObj == null)
            jidObj = getObj(key, "chatJid");
        if (jidObj == null)
            jidObj = getObj(key, "A00");
        if (jidObj != null)
            chatJid = jidObj.toString();
        if (chatJid != null && (chatJid.equalsIgnoreCase("false") || chatJid.equalsIgnoreCase("true"))) {
            chatJid = null;
        }

        // 4. Extract isFromMe
        boolean fromMe = false;
        Field fmField = findField(key.getClass(), "fromMe");
        if (fmField == null)
            fmField = findField(key.getClass(), "isFromMe");
        if (fmField == null)
            fmField = findField(key.getClass(), "A02");
        if (fmField != null) {
            Object v = fmField.get(key);
            if (v instanceof Boolean)
                fromMe = (Boolean) v;
        }

        // 5. Media Type (Moved up to prioritize detection)
        int mediaType = -1;
        Field mtf = findField(msgClass, "mediaType");
        if (mtf == null)
            mtf = findField(msgClass, "media_wa_type");
        if (mtf != null) {
            try {
                mediaType = mtf.getInt(msg);
            } catch (Exception ignored) {
            }
        }

        // 6. Extract Text Body
        String textContent = getStr(msg, "text");
        if (textContent == null)
            textContent = getStr(msg, "body");
        if (textContent == null)
            textContent = getStr(msg, "A0Q");

        // Safety check: If it's a media message, discard "text" if it looks like a URL
        // or Hash
        if (mediaType > 0 && textContent != null) {
            if (textContent.startsWith("http") || (textContent.length() > 20 && !textContent.contains(" "))) {
                textContent = null;
            }
        }

        // Heuristic search: Only if text is null AND it's NOT a media message
        if (textContent == null && mediaType <= 0) {
            String bestCandidate = null;
            Class<?> cls = msgClass;
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType().equals(String.class)) {
                        f.setAccessible(true);
                        try {
                            String val = (String) f.get(msg);
                            if (val != null && !val.isEmpty()) {
                                // Determine if this value is safe (not a URL, not a hash)
                                boolean isUrl = val.startsWith("http") || val.startsWith("www.");
                                boolean isHash = val.length() > 20 && !val.contains(" ");

                                if (!isUrl && !isHash) {
                                    if (bestCandidate == null || val.length() > bestCandidate.length()) {
                                        if (val.length() > 1)
                                            bestCandidate = val;
                                    }
                                }
                            }
                        } catch (IllegalAccessException e) {
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
            if (bestCandidate != null)
                textContent = bestCandidate;
        }

        // 7. Sender JID
        String senderJid = fromMe ? "Me" : chatJid;
        Object participant = getObj(msg, "participant");
        if (participant == null)
            participant = getObj(msg, "senderJid");
        if (participant == null)
            participant = getObj(msg, "A0b");
        if (participant != null) {
            String val = participant.toString();
            if (!val.equalsIgnoreCase("false") && !val.equalsIgnoreCase("true")) {
                senderJid = val;
            }
        }

        // 8. Media Details
        String mediaPath = null;
        Object mf = getObj(msg, "mediaData");
        if (mf == null)
            mf = getObj(msg, "mediaFile");
        if (mf instanceof File && ((File) mf).exists()) {
            mediaPath = ((File) mf).getAbsolutePath();
        }

        String mediaCaption = getStr(msg, "caption");
        if (mediaCaption == null)
            mediaCaption = getStr(msg, "mediaCaption");
        if (mediaCaption == null)
            mediaCaption = getStr(msg, "A03");

        long timestamp = System.currentTimeMillis();

        // 9. Contact Name Resolution
        String contactName = null;
        try {
            // Priority 1: Current Chat Room Title (Most Reliable as per User Suggestion)
            contactName = com.wmods.wppenhacer.xposed.core.WppCore.getCurrentChatTitle();

            // Priority 2: WaContactWpp Internal Lookup (New Reliable Fallback)
            if (contactName == null && chatJid != null) {
                try {
                    XposedBridge.log("WAE: Attempting WaContactWpp lookup for " + chatJid);
                    FMessageWpp.UserJid userJidObj = new FMessageWpp.UserJid(chatJid);
                    WaContactWpp waContact = WaContactWpp.getWaContactFromJid(userJidObj);
                    if (waContact != null) {
                        contactName = waContact.getDisplayName();
                        if (contactName == null || contactName.isEmpty()) {
                            contactName = waContact.getWaName();
                        }
                        XposedBridge.log("WAE: WaContact result: " + contactName);
                    } else {
                        XposedBridge.log("WAE: WaContactWpp returned null for " + chatJid);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("WAE: WaContactWpp lookup failed: " + t.getMessage());
                }
            }

            // Priority 3: Simple ContactsContract lookup (Fallback)
            if (contactName == null && chatJid != null && !chatJid.contains("@g.us")) {
                contactName = getContactName(context, chatJid);
            }
        } catch (Throwable t) {
            XposedBridge.log("WAE: Error in contact resolution: " + t.getMessage());
        }

        // Capture Package Name
        String packageName = context.getPackageName();

        // Create and Save
        DeletedMessage deletedMessage = new DeletedMessage(
                0, keyId, chatJid, senderJid, timestamp, mediaType, textContent, mediaPath, mediaCaption, fromMe,
                contactName, packageName);

        saveToDatabase(context, deletedMessage);
    }

    private String getContactName(Context context, String jid) {
        if (jid == null)
            return null;
        String phoneNumber = jid.replace("@s.whatsapp.net", "").replace("@g.us", "");
        if (phoneNumber.contains("@"))
            phoneNumber = phoneNumber.split("@")[0];

        try {
            android.net.Uri uri = android.net.Uri.withAppendedPath(
                    android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(phoneNumber));
            String[] projection = new String[] { android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME };

            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                    null)) {
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
            values.put("contact_name", message.getContactName());
            values.put("package_name", message.getPackageName());

            android.net.Uri uri = android.net.Uri.parse("content://com.wmods.wppenhacer.provider/deleted_messages");
            context.getContentResolver().insert(uri, values);
            XposedBridge.log("WAE: RecoverDeleteForMe saved via Provider: id=" + message.getKeyId() + " text="
                    + message.getTextContent());
        } catch (Exception e) {
            XposedBridge.log("WAE: Failed to insert to provider: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Scans hierarchy for ALL fields of type, returns first non-null value
    private Object getFirstNonNullFieldByType(Object target, Class<?> type) {
        if (target == null || type == null)
            return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(type)) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(target);
                        if (val != null)
                            return val;
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
        if (target == null)
            return null;
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType().equals(String.class)) {
                    f.setAccessible(true);
                    try {
                        String val = (String) f.get(target);
                        if (val != null && !val.isEmpty())
                            return val;
                    } catch (IllegalAccessException e) {
                    }
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
            if (f == null)
                return null;
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getObj(Object obj, String name) {
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null)
                return null;
            return f.get(obj);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getPluginName() {
        return "Recover Delete For Me";
    }

    public static void restoreMessage(android.content.Context context, DeletedMessage message) {
        try {
            if (message.getTextContent() != null && !message.getTextContent().isEmpty()) {
                android.widget.Toast
                        .makeText(context, "Message: " + message.getTextContent(), android.widget.Toast.LENGTH_LONG)
                        .show();
            } else {
                android.widget.Toast
                        .makeText(context, "Media restore not supported yet", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            XposedBridge.log("WAE: Restore failed: " + e.getMessage());
        }
    }
}
