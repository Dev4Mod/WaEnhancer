package com.wmods.wppenhacer.xposed.features.general;

import android.content.ContentValues;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChatLimit extends Feature {
    public ChatLimit(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var antiDisappearing = prefs.getBoolean("antidisappearing", false);
        var revokeallmessages = prefs.getBoolean("revokeallmessages", false);

        var chatLimitDeleteMethod = Unobfuscator.loadChatLimitDeleteMethod(classLoader);
        var chatLimitDelete2Method = Unobfuscator.loadChatLimitDelete2Method(classLoader);
        var epUpdateMethod = Unobfuscator.loadEphemeralInsertdb(classLoader);

        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (antiDisappearing) {
                    MessageStore.getInstance().executeSQL("UPDATE message_ephemeral SET expire_timestamp = 2553512370000");
                }
            }
        });

        XposedBridge.hookMethod(epUpdateMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (antiDisappearing) {
                    var contentValues = (ContentValues) param.getResult();
                    contentValues.put("expire_timestamp", 2553512370000L);
                }
            }
        });


//        var chatLimitEditClass = Unobfuscator.loadChatLimitEditClass(loader);

//        if (prefs.getBoolean("editallmessages", false)) {
//            Others.propsInteger.put(2983, Integer.MAX_VALUE);
//            Others.propsInteger.put(3272, Integer.MAX_VALUE);
//        }

        XposedBridge.hookMethod(chatLimitDeleteMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (ReflectionUtils.isCalledFromMethod(chatLimitDelete2Method) && revokeallmessages) {
                    param.setResult(0L);
                }
            }
        });

        var seeMoreMethod = Unobfuscator.loadSeeMoreConstructor(classLoader);
        XposedBridge.hookMethod(seeMoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("removeseemore", false)) return;
                param.args[1] = Integer.MAX_VALUE;
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Limit";
    }
}
