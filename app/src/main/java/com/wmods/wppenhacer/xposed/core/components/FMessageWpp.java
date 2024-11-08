package com.wmods.wppenhacer.xposed.core.components;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class FMessageWpp {

    public static Class<?> TYPE;
    private static boolean initialized;
    private static Method userJidMethod;
    private static Field keyMessage;
    private static Field getFieldIdMessage;
    private static Method deviceJidMethod;
    private static Method messageMethod;
    private static Method messageWithMediaMethod;
    private static Field mediaTypeField;
    private static Method getOriginalMessageKey;
    private static Class abstractMediaMessageClass;
    private final Object fmessage;

    public FMessageWpp(Object fMessage) {
        if (fMessage == null) throw new RuntimeException("fMessage is null");
        this.fmessage = fMessage;
        try {
            init(fMessage.getClass().getClassLoader());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void init(ClassLoader classLoader) throws Exception {
        if (initialized) return;
        initialized = true;
        TYPE = Unobfuscator.loadFMessageClass(classLoader);
        var userJidClass = classLoader.loadClass("com.whatsapp.jid.UserJid");
        userJidMethod = ReflectionUtils.findMethodUsingFilter(TYPE, method -> method.getParameterCount() == 0 && method.getReturnType() == userJidClass);
        keyMessage = Unobfuscator.loadMessageKeyField(classLoader);
        Key.TYPE = keyMessage.getType();
        messageMethod = Unobfuscator.loadNewMessageMethod(classLoader);
        messageWithMediaMethod = Unobfuscator.loadNewMessageWithMediaMethod(classLoader);
        getFieldIdMessage = Unobfuscator.loadSetEditMessageField(classLoader);
        deviceJidMethod = ReflectionUtils.findMethodUsingFilter(TYPE, method -> method.getReturnType().equals(XposedHelpers.findClass("com.whatsapp.jid.DeviceJid", classLoader)));
        mediaTypeField = Unobfuscator.loadMediaTypeField(classLoader);
        getOriginalMessageKey = Unobfuscator.loadOriginalMessageKey(classLoader);
        abstractMediaMessageClass = Unobfuscator.loadAbstractMediaMessageClass(classLoader);
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
            return deviceJidMethod.invoke(fmessage);
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
            return new Key(keyMessage.get(fmessage));
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


    public int getMediaType() {
        try {
            return mediaTypeField.getInt(fmessage);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return -1;
    }

    public static class Key {
        public static Class<?> TYPE;

        public final Object thisObject;
        public final String messageID;
        public final boolean isFromMe;
        public final Object remoteJid;

        public Key(Object key) {
            this.thisObject = key;
            this.messageID = (String) XposedHelpers.getObjectField(key, "A01");
            this.isFromMe = XposedHelpers.getBooleanField(key, "A02");
            this.remoteJid = XposedHelpers.getObjectField(key, "A00");
        }

    }

}
