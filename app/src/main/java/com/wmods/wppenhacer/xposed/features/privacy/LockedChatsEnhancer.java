package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class LockedChatsEnhancer extends Feature {

    private Object chatCache;

    public LockedChatsEnhancer(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("lockedchats_enhancer", false)) return;

        Method jidNotifications = Unobfuscator.loadNotificationMethod(classLoader);
        Method lockedChatsMethod = Unobfuscator.loadLockedChatsMethod(classLoader);

        XposedBridge.hookMethod(jidNotifications, new XC_MethodHook() {
            private Unhook unhook;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                this.unhook = XposedBridge.hookMethod(lockedChatsMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(new ArrayList<>());
                    }
                });
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                unhook.unhook();
            }
        });

        var chatCacheClass = Unobfuscator.loadChatCacheClass(classLoader);
        var lockedChatsFields = ReflectionUtils.findAllFieldsUsingFilter(chatCacheClass, f -> f.getType() == HashSet.class);

        XposedBridge.hookAllConstructors(chatCacheClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                chatCache = param.thisObject;
            }
        });

        var loadedContacts = Unobfuscator.loadLoadedContactsMethod(classLoader);

        XposedBridge.hookMethod(loadedContacts, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var list = (List) XposedHelpers.getObjectField(param.args[0], "A01");
                HashSet<?> lockedChats = (HashSet<?>) lockedChatsFields[1].get(chatCache);
                var lockedNumbers = lockedChats.stream().map(userjid -> new FMessageWpp.UserJid(userjid).getPhoneNumber()).collect(Collectors.toList());
                list.removeIf(item -> {
                    if (!WaContactWpp.TYPE.isInstance(item)) return false;
                    var waContact = new WaContactWpp(item);
                    var phoneNumber = waContact.getUserJid().getPhoneNumber();
                    return lockedNumbers.contains(phoneNumber);
                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Locked Chats Enhancer";
    }
}
