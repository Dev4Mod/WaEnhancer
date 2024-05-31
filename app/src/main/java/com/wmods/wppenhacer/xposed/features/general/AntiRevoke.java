package com.wmods.wppenhacer.xposed.features.general;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.DesignUtils;
import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.UnobfuscatorCache;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AntiRevoke extends Feature {

    private static final HashMap<String, HashSet<String>> messageRevokedMap = new HashMap<>();
    private static Field fieldMessageKey;
    private static Field getFieldIdMessage;

    public AntiRevoke(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        var antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(antiRevokeMessageMethod));

        var classThreadMessage = Unobfuscator.loadFMessageClass(loader);
        logDebug("Class: " + classThreadMessage);

        fieldMessageKey = Unobfuscator.loadMessageKeyField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(fieldMessageKey));

        getFieldIdMessage = Unobfuscator.loadSetEditMessageField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(getFieldIdMessage));


        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));

        var unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(unknownStatusPlaybackMethod));

        var statusPlaybackField = Unobfuscator.loadStatusPlaybackViewField(loader);
        logDebug(Unobfuscator.getFieldDescriptor(statusPlaybackField));

        XposedBridge.hookMethod(antiRevokeMessageMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Exception {
                var objMessage = classThreadMessage.cast(param.args[0]);
                var fieldMessageDetails = fieldMessageKey.get(objMessage);
                var deviceJidMethod = ReflectionUtils.findMethodUsingFilter(fieldMessageKey.getDeclaringClass(), method -> method.getReturnType().equals(XposedHelpers.findClass("com.whatsapp.jid.DeviceJid", loader)));
                var deviceJid = ReflectionUtils.callMethod(deviceJidMethod, objMessage);
                var isFromMe = XposedHelpers.getBooleanField(fieldMessageDetails, "A02");
                var userJid = XposedHelpers.getObjectField(fieldMessageDetails, "A00");
                var rawString = WppCore.getRawString(userJid);
                if (WppCore.isGroup(rawString)) {
                    if (deviceJid != null && antiRevoke(objMessage) != 0) {
                        param.setResult(true);
                    }
                } else if (!isFromMe && antiRevoke(objMessage) != 0) {
                    param.setResult(true);
                }
            }
        });


        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var objMessage = param.args[2];
                var dateTextView = (TextView) param.args[1];
                isMRevoked(objMessage, dateTextView, "antirevoke");
            }
        });

        XposedBridge.hookMethod(unknownStatusPlaybackMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var obj = param.args[1];
                var objMessage = param.args[0];
                Object objView = statusPlaybackField.get(obj);
                Field[] textViews = Arrays.stream(statusPlaybackField.getType().getDeclaredFields()).filter(f -> f.getType() == TextView.class).toArray(Field[]::new);
                if (textViews == null) {
                    log("Could not find TextView");
                    return;
                }
                int dateId = Utils.getID("date", "id");
                for (Field textView : textViews) {
                    TextView textView1 = (TextView) XposedHelpers.getObjectField(objView, textView.getName());
                    if (textView1 == null || textView1.getId() == dateId) {
                        isMRevoked(objMessage, textView1, "antirevokestatus");
                        break;
                    }
                }
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Anti Revoke";
    }

    public static Drawable scaleImage(Resources resources, Drawable image, float scaleFactor) {
        if (!(image instanceof BitmapDrawable)) {
            return image;
        }
        Bitmap b = ((BitmapDrawable) image).getBitmap();
        int sizeX = Math.round(image.getIntrinsicWidth() * scaleFactor);
        int sizeY = Math.round(image.getIntrinsicHeight() * scaleFactor);
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, sizeX, sizeY, false);
        return new BitmapDrawable(resources, bitmapResized);
    }

    private static void saveRevokedMessage(String authorJid, String messageKey, Object objMessage) {
        HashSet<String> messages = getRevokedMessages(objMessage);
        messages.add(messageKey);
        DelMessageStore.getInstance(Utils.getApplication()).insertMessage(authorJid, messageKey, System.currentTimeMillis());
    }

    private static HashSet<String> getRevokedMessages(Object objMessage) {
        String jid = stripJID(getJidAuthor(objMessage));
        if (messageRevokedMap.containsKey(jid)) {
            return messageRevokedMap.get(jid);
        }
        var messages = DelMessageStore.getInstance(Utils.getApplication()).getMessagesByJid(jid);
        if (messages == null) messages = new HashSet<>();
        messageRevokedMap.put(jid, messages);
        return messages;
    }

    public static String stripJID(String str) {
        try {
            return (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast")) ? str.substring(0, str.indexOf("@")) : str;
        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
            return str;
        }
    }

    public static String getJidAuthor(Object objMessage) {
        Object fieldMessageDetails = XposedHelpers.getObjectField(objMessage, fieldMessageKey.getName());
        Object fieldMessageAuthorJid = XposedHelpers.getObjectField(fieldMessageDetails, "A00");
        if (fieldMessageAuthorJid == null) return "";
        else return WppCore.getRawString(fieldMessageAuthorJid);
    }


    private void isMRevoked(Object objMessage, TextView dateTextView, String antirevokeType) {
        if (dateTextView == null) return;
        var fieldMessageDetails = XposedHelpers.getObjectField(objMessage, fieldMessageKey.getName());
        var messageKey = (String) XposedHelpers.getObjectField(fieldMessageDetails, "A01");
        var messageRevokedList = getRevokedMessages(objMessage);
        var id = XposedHelpers.getLongField(objMessage, getFieldIdMessage.getName());
        String keyOrig = null;
        if (messageRevokedList.contains(messageKey) || ((keyOrig = MessageStore.getOriginalMessageKey(id)) != null && messageRevokedList.contains(keyOrig))) {
            var timestamp = DelMessageStore.getInstance(Utils.getApplication()).getTimestampByMessageId(keyOrig == null ? messageKey : keyOrig);
            if (timestamp > 0) {
                Locale locale = Utils.getApplication().getResources().getConfiguration().getLocales().get(0);
                DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
                var date = dateFormat.format(new Date(timestamp));
                dateTextView.getPaint().setUnderlineText(true);
                dateTextView.setOnClickListener(v -> Utils.showToast(String.format(Utils.getApplication().getString(ResId.string.message_removed_on), date), Toast.LENGTH_LONG));
            }
            var antirevokeValue = Integer.parseInt(prefs.getString(antirevokeType, "0"));
            if (antirevokeValue == 1) {
                // Text
                var newTextData = UnobfuscatorCache.getInstance().getString("messagedeleted") + " | " + dateTextView.getText();
                dateTextView.setText(newTextData);
            } else if (antirevokeValue == 2) {
                // Icon
                var icon = DesignUtils.getDrawableByName("msg_status_client_revoked");
                var drawable = scaleImage(Utils.getApplication().getResources(), icon, 0.7f);
                drawable.setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP));
                dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                dateTextView.setCompoundDrawablePadding(5);
            }
        } else {
            dateTextView.setCompoundDrawables(null, null, null, null);
            var revokeNotice = UnobfuscatorCache.getInstance().getString("messagedeleted") + " | ";
            var dateText = dateTextView.getText().toString();
            if (dateText.contains(revokeNotice)) {
                dateTextView.setText(dateText.replace(revokeNotice, ""));
            }
            dateTextView.getPaint().setUnderlineText(false);
            dateTextView.setOnClickListener(null);
        }
    }


    private int antiRevoke(Object objMessage) {
        showToast(objMessage);
        var messageKey = (String) XposedHelpers.getObjectField(objMessage, "A01");
        var stripJID = stripJID(getJidAuthor(objMessage));
        var revokeboolean = stripJID.equals("status") ? Integer.parseInt(prefs.getString("antirevokestatus", "0")) : Integer.parseInt(prefs.getString("antirevoke", "0"));
        if (revokeboolean == 0) return revokeboolean;

        var messageRevokedList = getRevokedMessages(objMessage);
        if (!messageRevokedList.contains(messageKey)) {
            try {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    saveRevokedMessage(stripJID, messageKey, objMessage);
                    try {
                        var mConversation = WppCore.getCurrenConversation();
                        if (mConversation != null && WppCore.stripJID(WppCore.getCurrentRawJID()).equals(stripJID)) {
                            mConversation.runOnUiThread(() -> {
                                if (mConversation.hasWindowFocus()) {
                                    mConversation.startActivity(mConversation.getIntent());
                                    mConversation.overridePendingTransition(0, 0);
                                    mConversation.getWindow().getDecorView().findViewById(android.R.id.content).postInvalidate();
                                } else {
                                    mConversation.recreate();
                                }
                            });
                        }
                    } catch (Exception e) {
                        XposedBridge.log(e.getMessage());
                    }
                });
            } catch (Exception e) {
                XposedBridge.log(e.getMessage());
            }
        }
        return revokeboolean;
    }

    private void showToast(Object fMessage) {
        if (!prefs.getBoolean("toastdeleted", false)) return;
        var jidAuthor = getJidAuthor(fMessage);

        var messageSuffix = Utils.getApplication().getString(ResId.string.deleted_message);
        if (Objects.equals(stripJID(jidAuthor), "status")) {
            messageSuffix = Utils.getApplication().getString(ResId.string.deleted_status);
            var getUserJid = ReflectionUtils.findMethodUsingFilter(fieldMessageKey.getDeclaringClass(), method -> method.getReturnType().equals(XposedHelpers.findClass("com.whatsapp.jid.UserJid", loader)));
            jidAuthor = WppCore.getRawString(ReflectionUtils.callMethod(getUserJid, fMessage));
        }
        if (TextUtils.isEmpty(jidAuthor)) return;
        String name = WppCore.getContactName(WppCore.createUserJid(jidAuthor));
        if (TextUtils.isEmpty(name)) {
            name = stripJID(jidAuthor);
        }
        String message = name + " " + messageSuffix;
        Utils.showToast(message, Toast.LENGTH_SHORT);
    }

}
