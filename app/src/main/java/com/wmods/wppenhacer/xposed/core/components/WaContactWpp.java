package com.wmods.wppenhacer.xposed.core.components;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public record WaContactWpp(Object mInstance) {

    public static Class<?> TYPE;

    // Fields
    private static Field fieldContactData;
    private static Field fieldUserJid;
    private static Field fieldGetWaName;

    // Methods
    private static Method getWaContactMethod;
    private static Method methodGetDisplayName;
    private static Method getProfilePhoto;

    // Instances
    private static Object mInstanceGetWaContact;
    private static Object mInstanceGetProfilePhoto;


    public WaContactWpp(Object mInstance) {
        if (TYPE == null) throw new RuntimeException("WaContactWpp not initialized");
        if (mInstance == null) throw new RuntimeException("object is null");
        if (!TYPE.isInstance(mInstance)) throw new RuntimeException("object is not a WaContactWpp");
        this.mInstance = TYPE.cast(mInstance);
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            TYPE = Unobfuscator.loadWaContactClass(classLoader);
            var classPhoneUserJid = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.PhoneUserJid");
            var classJid = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");

            var phoneUserJid = ReflectionUtils.getFieldByExtendType(TYPE, classPhoneUserJid);
            if (phoneUserJid == null) {
                var contactDataClass = Unobfuscator.loadWaContactData(classLoader);
                fieldContactData = ReflectionUtils.getFieldByType(TYPE, contactDataClass);
                fieldUserJid = ReflectionUtils.getFieldByExtendType(contactDataClass, classJid);
            } else {
                fieldUserJid = ReflectionUtils.getFieldByExtendType(TYPE, classJid);
            }
            methodGetDisplayName = Unobfuscator.loadWaContactDisplayNameMethod(classLoader);
            fieldGetWaName = Unobfuscator.loadWaContactGetWaNameField(classLoader);

            getWaContactMethod = Unobfuscator.loadGetWaContactMethod(classLoader);
            XposedBridge.hookAllConstructors(getWaContactMethod.getDeclaringClass(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mInstanceGetWaContact = param.thisObject;
                }
            });
            getProfilePhoto = Unobfuscator.loadGetProfilePhotoMethod(classLoader);

            XposedBridge.hookAllConstructors(getProfilePhoto.getDeclaringClass(), new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mInstanceGetProfilePhoto = param.thisObject;
                }
            });

        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    public Object getObject() {
        return mInstance;
    }


    public FMessageWpp.UserJid getUserJid() {
        try {
            if (fieldContactData != null) {
                var coreData = fieldContactData.get(mInstance);
                var userjid = fieldUserJid.get(coreData);
                return new FMessageWpp.UserJid(userjid);
            }
            return new FMessageWpp.UserJid(fieldUserJid.get(mInstance));
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public String getDisplayName() {
        try {
            return (String) methodGetDisplayName.invoke(mInstance);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public String getWaName() {
        try {
            return (String) fieldGetWaName.get(mInstance);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public static WaContactWpp getWaContactFromJid(FMessageWpp.UserJid userJid) {
        try {
            return new WaContactWpp(getWaContactMethod.invoke(mInstanceGetWaContact, userJid.userJid));
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

    public File getProfilePhoto() {
        try {
            return (File) getProfilePhoto.invoke(mInstanceGetProfilePhoto, mInstance);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
        return null;
    }

}
