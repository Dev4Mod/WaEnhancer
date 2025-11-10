package com.wmods.wppenhacer.xposed.core.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * @noinspection unused
 */
public class FMessageWpp {

    public static Class<?> TYPE;
    private static Method userJidMethod;
    private static Field keyMessage;
    private static Field getFieldIdMessage;
    private static Field deviceJidField;
    private static Method messageMethod;
    private static Method messageWithMediaMethod;
    private static Field mediaTypeField;
    private static Method getOriginalMessageKey;
    private static Class abstractMediaMessageClass;
    private static Field broadcastField;
    private final Object fmessage;
    private Key key;

    public FMessageWpp(Object fMessage) {
        if (fMessage == null) throw new RuntimeException("Object fMessage is null");
        if (!FMessageWpp.TYPE.isInstance(fMessage))
            throw new RuntimeException("Object fMessage is not a FMessage Instance");
        this.fmessage = fMessage;
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            TYPE = Unobfuscator.loadFMessageClass(classLoader);
            var userJidClass = classLoader.loadClass("com.whatsapp.jid.UserJid");
            userJidMethod = ReflectionUtils.findMethodUsingFilter(TYPE, method -> method.getParameterCount() == 0 && method.getReturnType() == userJidClass);
            keyMessage = Unobfuscator.loadMessageKeyField(classLoader);
            Key.TYPE = keyMessage.getType();
            messageMethod = Unobfuscator.loadNewMessageMethod(classLoader);
            messageWithMediaMethod = Unobfuscator.loadNewMessageWithMediaMethod(classLoader);
            getFieldIdMessage = Unobfuscator.loadSetEditMessageField(classLoader);
            var deviceJidClass = XposedHelpers.findClass("com.whatsapp.jid.DeviceJid", classLoader);
            deviceJidField = ReflectionUtils.findFieldUsingFilter(TYPE, field -> field.getType() == deviceJidClass);
            mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader);
            getOriginalMessageKey = Unobfuscator.loadOriginalMessageKey(classLoader);
            abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
            broadcastField = Unobfuscator.loadBroadcastTagField(classLoader);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public static boolean checkUnsafeIsFMessage(ClassLoader classLoader, Class<?> clazz) throws Exception {
        Class<?> FmessageClass = Unobfuscator.loadFMessageClass(classLoader);
        if (FmessageClass.isAssignableFrom(clazz)) return true;
        var interfaces = FmessageClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            if (anInterface == clazz) return true;
        }
        return false;
    }


    public UserJid getUserJid() {
        try {
            return new UserJid(userJidMethod.invoke(fmessage));
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public Object getDeviceJid() {
        try {
            return deviceJidField.get(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public long getRowId() {
        try {
            return getFieldIdMessage.getLong(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return 0;
    }


    public Key getKey() {
        try {
            if (this.key == null)
                this.key = new Key(keyMessage.get(fmessage));
            return key;
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public Key getOriginalKey() {
        try {
            return new Key(getOriginalMessageKey.invoke(fmessage));
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public boolean isBroadcast() {
        try {
            return broadcastField.getBoolean(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return false;
    }

    public Object getObject() {
        return fmessage;
    }

    public String getMessageStr() {
        try {
            var message = (String) messageMethod.invoke(fmessage);
            if (message != null) return message;
            return (String) messageWithMediaMethod.invoke(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
            return null;
        }
    }

    /**
     * @noinspection BooleanMethodIsAlwaysInverted
     */
    public boolean isMediaFile() {
        try {
            return abstractMediaMessageClass.isInstance(fmessage);
        } catch (Exception e) {
            return false;
        }
    }

    public File getMediaFile() {
        try {
            if (!isMediaFile()) return null;
            for (var field : abstractMediaMessageClass.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                var fileField = ReflectionUtils.getFieldByType(field.getType(), File.class);
                if (fileField != null) {
                    var mediaObject = ReflectionUtils.getObjectField(field, fmessage);
                    var mediaFile = (File) fileField.get(mediaObject);
                    if (mediaFile != null) return mediaFile;
                    var filePath = MessageStore.getInstance().getMediaFromID(getRowId());
                    if (filePath == null) return null;
                    return new File(filePath);
                }
            }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    /**
     * Gets the media type of the message.
     * Media type values:
     * 2 = Voice note
     * 82 = View once voice note
     * 42 = View once image
     * 43 = View once video
     *
     * @return The media type as an integer, or -1 if an error occurs
     */
    public int getMediaType() {
        try {
            return mediaTypeField.getInt(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return -1;
    }

    public boolean isViewOnce() {
        var media_type = getMediaType();
        return (media_type == 82 || media_type == 42 || media_type == 43);
    }

    /*
     * Represents the key of a WhatsApp message, containing identifiers for the message.
     */
    public static class Key {

        /**
         * The class type of the key object.
         */
        public static Class<?> TYPE;
        /**
         * The underlying key object from WhatsApp's code.
         */
        public Object thisObject;
        /**
         * The unique identifier for the message.
         */
        public String messageID;
        /**
         * A boolean indicating if the message was sent by the current user.
         */
        public boolean isFromMe;
        /**
         * The JID of whatsapp
         */
        public UserJid remoteJid;

        /**
         * Constructs a new Key instance by wrapping the original WhatsApp message key object.
         *
         * @param key The original message key object.
         */
        public Key(Object key) {
            this.thisObject = key;
            this.messageID = (String) XposedHelpers.getObjectField(key, "A01");
            this.isFromMe = XposedHelpers.getBooleanField(key, "A02");
            this.remoteJid = new UserJid(XposedHelpers.getObjectField(key, "A00"));
        }

    }

    public static class UserJid {

        public Object phoneJid;

        public Object userJid;

        public UserJid(@Nullable String rawjid) {
            if (rawjid == null) return;
            if (checkValidLID(rawjid)) {
                this.userJid = WppCore.createUserJid(rawjid);
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid);
            } else {
                this.phoneJid = WppCore.createUserJid(rawjid);
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid);
            }
        }

        public UserJid(@Nullable Object lidOrJid) {
            if (lidOrJid == null) return;
            if (checkValidLID((String) XposedHelpers.callMethod(lidOrJid, "getRawString"))) {
                this.userJid = lidOrJid;
                this.phoneJid = WppCore.getPhoneJidFromUserJid(this.userJid);
            } else {
                this.phoneJid = lidOrJid;
                this.userJid = WppCore.getUserJidFromPhoneJid(this.phoneJid);
            }
        }

        public UserJid(@Nullable Object userJid, Object phoneJid) {
            this.userJid = userJid;
            this.phoneJid = phoneJid;
        }


        @Nullable
        public String getPhoneRawString() {
            if (this.phoneJid == null) return null;
            String raw = (String) XposedHelpers.callMethod(this.phoneJid, "getRawString");
            if (raw == null) return null;
            return raw.replaceFirst("\\.[\\d:]+@", "@");
        }

        @Nullable
        public String getUserRawString() {
            if (this.phoneJid == null) return null;
            String raw = (String) XposedHelpers.callMethod(this.userJid, "getRawString");
            if (raw == null) return null;
            return raw.replaceFirst("\\.[\\d:]+@", "@");
        }

        @Nullable
        public String getPhoneNumber() {
            var str = getPhoneRawString();
            try {
                if (str == null) return null;
                if (str.contains(".") && str.contains("@") && str.indexOf(".") < str.indexOf("@")) {
                    return str.substring(0, str.indexOf("."));
                } else if (str.contains("@g.us") || str.contains("@s.whatsapp.net") || str.contains("@broadcast") || str.contains("@lid")) {
                    return str.substring(0, str.indexOf("@"));
                }
                return str;
            } catch (Exception e) {
                XposedBridge.log(e);
                return str;
            }
        }

        public boolean isStatus() {
            return Objects.equals(getPhoneNumber(), "status");
        }

        public boolean isNewsletter() {
            String raw = getPhoneRawString();
            if (raw == null) return false;
            return raw.contains("@newsletter");
        }


        public boolean isGroup() {
            if (this.phoneJid == null) return false;
            String str = getPhoneRawString();
            if (str == null) return false;
            return str.contains("-") || str.contains("@g.us") || (!str.contains("@") && str.length() > 16);
        }


        public boolean isNull() {
            return this.phoneJid == null && this.userJid == null;
        }

        private static boolean checkValidLID(String lid) {
            if (lid != null && lid.contains("@lid")) {
                String id = lid.split("@")[0];
                return lid.length() > 14;
            }
            return false;
        }

        @NonNull
        @Override
        public String toString() {
            return "UserJid{" +
                    "PhoneJid=" + phoneJid +
                    ", UserJid=" + userJid +
                    '}';
        }
    }

}
