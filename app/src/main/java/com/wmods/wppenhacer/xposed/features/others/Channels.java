package com.wmods.wppenhacer.xposed.features.others;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import org.luckypray.dexkit.util.DexSignUtil;

import java.util.ArrayList;
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
        var removechannelRec = prefs.getBoolean("removechannel_rec", false);
        if (channels) {
            var headerChannelItem = Unobfuscator.loadHeaderChannelItemClass(classLoader);
            log("HeaderChannelItem: " + headerChannelItem);
            var listChannelItem = Unobfuscator.loadListChannelItemClass(classLoader);
            log("ListChannelItem: " + listChannelItem);
            var listUpdateItems = Unobfuscator.loadListUpdateItemsConstructor(classLoader);
            log("ListUpdateItems: " + DexSignUtil.getConstructorSign(listUpdateItems));
            XposedBridge.hookMethod(listUpdateItems,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[0] instanceof ArrayList<?> list) {
                                list.removeIf((e) -> headerChannelItem.isInstance(e) || listChannelItem.isInstance(e));
                            }
                        }
                    });
        }
        if (removechannelRec || channels) {
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
