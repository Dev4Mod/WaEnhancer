package com.wmods.wppenhacer.xposed.features.others;

import static com.wmods.wppenhacer.xposed.features.general.Others.propsInteger;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class Channels extends Feature {
    public Channels(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var channels = prefs.getBoolean("channels", false);
        if (channels) {
            var igstatus = prefs.getBoolean("igstatus", false);
            propsInteger.put(3877, channels ? igstatus ? 2 : 0 : 2);
            return;
        }
        var removechannelRec = prefs.getBoolean("removechannel_rec", false);
        if (removechannelRec) {
            var removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(classLoader);
            XposedBridge.hookAllConstructors(removeChannelRecClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof List list) {
                        if (list.isEmpty()) return;
                        list.clear();
                    }
                }
            });
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Channels";
    }
}
