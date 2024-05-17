package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Feature;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ChatLimit extends Feature {
    public ChatLimit(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var chatLimitDeleteMethod = Unobfuscator.loadChatLimitDeleteMethod(loader);
        var chatLimitDelete2Method = Unobfuscator.loadChatLimitDelete2Method(loader);
//        var chatLimitEditClass = Unobfuscator.loadChatLimitEditClass(loader);

        XposedBridge.hookMethod(chatLimitDeleteMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Unobfuscator.isCalledFromMethod(chatLimitDelete2Method)) {
                    if (prefs.getBoolean("revokeallmessages", false))
                        param.setResult(0L);
                }
            }
        });

        var seeMoreMethod = Unobfuscator.loadSeeMoreMethod(loader);
        XposedBridge.hookMethod(seeMoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("removeseemore", false))return;
                param.args[0] = 0;
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Chat Limit";
    }
}
