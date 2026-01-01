package com.wmods.wppenhacer.xposed.core.components;

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;

import de.robv.android.xposed.XposedBridge;

public class WaContactWpp {

    public static Class<?> TYPE;
    private static Field fieldContactData;
    private static Field fieldUserJid;
    private final Object mInstance;


    public WaContactWpp(Object object) {
        if (TYPE == null) throw new RuntimeException("WaContactWpp not initialized");
        if (object == null) throw new RuntimeException("object is null");
        this.mInstance = object;
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

}
