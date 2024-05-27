package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class FreezeLastSeen extends Feature {
    public FreezeLastSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!prefs.getBoolean("freezelastseen", false) && !prefs.getBoolean("show_freezeLastSeen", false)) return;
        if (!WppCore.getPrivBoolean("freezelastseen", false) && prefs.getBoolean("show_freezeLastSeen", false)) return;
        var method = Unobfuscator.loadFreezeSeenMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(method));
        XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Freeze Last Seen";
    }

}
