package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class TypingPrivacy extends Feature {

    public TypingPrivacy(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        var ghostmode_t = prefs.getBoolean("ghostmode_t", false);
        var ghostmode_r = prefs.getBoolean("ghostmode_r", false);
        Method method = Unobfuscator.loadGhostModeMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                var p1 = ReflectionUtils.getArg(param.args, Integer.class, 0);
                var jidObj = ReflectionUtils.getArg(param.args, FMessageWpp.UserJid.TYPE_JID, 0);
                if (jidObj == null){
                    logDebug("UserJid not found in Typing Privacy");
                }
                var userJid = new FMessageWpp.UserJid(jidObj);
                var privacy = CustomPrivacy.getJSON(userJid.getPhoneNumber());
                var customHideTyping = privacy.optBoolean("HideTyping", ghostmode_t);
                var customHideRecording = privacy.optBoolean("HideRecording", ghostmode_r);
                if ((p1 == 1 && (customHideRecording || ghostmode)) || (p1 == 0 && (customHideTyping || ghostmode))) {
                    param.setResult(null);
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Typing Privacy";
    }
}
