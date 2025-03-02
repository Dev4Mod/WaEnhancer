package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class FreezeLastSeen extends Feature {
    public FreezeLastSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var freezeLastSeen = prefs.getBoolean("freezelastseen", false);
        var freezeLastSeenOption = WppCore.getPrivBoolean("freezelastseen", false);
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false) && prefs.getBoolean("ghostmode", false);

        if (freezeLastSeen || freezeLastSeenOption || ghostmode) {
            var method = Unobfuscator.loadFreezeSeenMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, XC_MethodReplacement.DO_NOTHING);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Freeze Last Seen";
    }

}
