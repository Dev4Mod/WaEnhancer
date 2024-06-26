package com.wmods.wppenhacer.xposed.features.others;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.util.DexSignUtil;

import java.util.ArrayList;

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
        var removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(classLoader);
        if (channels || removechannelRec) {
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
                            var list = ReflectionUtils.findArrayOfType(param.args, ArrayList.class);
                            if (list.isEmpty()) return;
                            var arrList = (ArrayList<?>) list.get(0).second;
                            arrList.removeIf((e) -> {
                                if (channels) {
                                    if (headerChannelItem.isInstance(e) || listChannelItem.isInstance(e))
                                        return true;
                                }
                                if (channels || removechannelRec) {
                                    return removeChannelRecClass.isInstance(e);
                                }
                                return false;
                            });
                            for (var item : arrList) {
                                XposedBridge.log(item.getClass().getName() + " -> " + item.toString().replace("\n", ""));
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
