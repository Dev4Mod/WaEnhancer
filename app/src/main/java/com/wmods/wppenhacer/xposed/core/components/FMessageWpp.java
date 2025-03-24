package com.wmods.wppenhacer.xposed.core.components;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** @noinspection unused*/
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

    public Object getUserJid() {
        try {
            return userJidMethod.invoke(fmessage);
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

    /** @noinspection BooleanMethodIsAlwaysInverted*/
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
                    var mediaFile = ReflectionUtils.getObjectField(field, fmessage);
                    return (File) fileField.get(mediaFile);
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

    /** @noinspection unused*/
    public static class Key {
        public static Class<?> TYPE;

        public Object thisObject;
        public String messageID;
        public boolean isFromMe;
        public Object remoteJid;

        public Key(Object key) {
            this.thisObject = key;
            this.messageID = (String) XposedHelpers.getObjectField(key, "A01");
            this.isFromMe = XposedHelpers.getBooleanField(key, "A02");
            this.remoteJid = XposedHelpers.getObjectField(key, "A00");
        }

        public void setIsFromMe(boolean value) {
            XposedHelpers.setBooleanField(thisObject, "A02", value);
            this.isFromMe = value;
        }

        public void setRemoteJid(Object value) {
            XposedHelpers.setObjectField(thisObject, "A00", value);
            this.remoteJid = value;
        }

        public void setMessageID(String messageID) {
            XposedHelpers.setObjectField(thisObject, "A01", messageID);
            this.messageID = messageID;
        }
    }

}
