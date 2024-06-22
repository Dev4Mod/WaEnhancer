package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class DndMode extends Feature {
    public DndMode(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        if (!WppCore.getPrivBoolean("dndmode",false)) return;
        var dndMethod = Unobfuscator.loadDndModeMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(dndMethod));
        XposedBridge.hookMethod(dndMethod, XC_MethodReplacement.DO_NOTHING);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Dnd Mode";
    }
}
