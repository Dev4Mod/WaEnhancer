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
        if (TYPE == null)
            throw new RuntimeException("WaContactWpp not initialized");
        if (mInstance == null)
            throw new RuntimeException("object is null");
        if (!TYPE.isInstance(mInstance))
            throw new RuntimeException("object is not a WaContactWpp");
        this.mInstance = TYPE.cast(mInstance);
    }

    public static void initialize(ClassLoader classLoader) {
        try {
            TYPE = Unobfuscator.loadWaContactClass(classLoader);
            var classPhoneUserJid = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "jid.PhoneUserJid");
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
            Class<?> contactManagerClass = getWaContactMethod.getDeclaringClass();

            // Try to find existing instance via static method (getInstance pattern)
            for (Method m : contactManagerClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getReturnType() == contactManagerClass
                        && m.getParameterCount() == 0) {
                    try {
                        Object instance = m.invoke(null);
                        if (instance != null) {
                            mInstanceGetWaContact = instance;
                            XposedBridge.log("WAE: WaContactWpp: Captured instance via static method: " + m.getName());
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (mInstanceGetWaContact == null) {
                XposedBridge.hookAllConstructors(contactManagerClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        mInstanceGetWaContact = param.thisObject;
                        XposedBridge.log("WAE: WaContactWpp: Captured instance via constructor");
                    }
                });
            } else {
                XposedBridge.log("WAE: WaContactWpp: Instance already captured, skipping constructor hook");
            }

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
        if (mInstanceGetWaContact == null) {
            XposedBridge.log("WAE: WaContactWpp: mInstanceGetWaContact is NULL. ContactManager not initialized?");
            return null;
        }
        try {
            Object contact = null;
            if (userJid.userJid != null) {
                contact = getWaContactMethod.invoke(mInstanceGetWaContact, userJid.userJid);
            }

            // Fallback to phoneJid if userJid lookup failed or userJid was null
            if (contact == null && userJid.phoneJid != null) {
                XposedBridge.log("WAE: WaContactWpp: userJid lookup failed, trying phoneJid");
                contact = getWaContactMethod.invoke(mInstanceGetWaContact, userJid.phoneJid);
            }

            if (contact != null) {
                return new WaContactWpp(contact);
            } else {
                XposedBridge.log("WAE: WaContactWpp: Contact lookup returned null for " + userJid);
            }
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
