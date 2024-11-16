package com.wmods.wppenhacer.xposed.features.others;

import android.view.Menu;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Channels extends Feature {
    public Channels(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    private static void removeItems(ArrayList<?> arrList, boolean channels,
                                    boolean removechannelRec, Class<?> headerChannelItem, Class<?> listChannelItem, Class<?>
                                            removeChannelRecClass) {
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
    }

    @Override
    public void doHook() throws Throwable {
        var channels = prefs.getBoolean("channels", false);
        var removechannelRec = prefs.getBoolean("removechannel_rec", false);
        if (channels || removechannelRec) {

            var removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(classLoader);
            log("RemoveChannelRec: " + removeChannelRecClass);
            var headerChannelItem = Unobfuscator.loadHeaderChannelItemClass(classLoader);
            log("HeaderChannelItem: " + headerChannelItem);
            var listChannelItem = Unobfuscator.loadListChannelItemClass(classLoader);
            log("ListChannelItem: " + listChannelItem);
            var listUpdateItems = Unobfuscator.loadListUpdateItems(classLoader);
            log("ListUpdateItems: " + Unobfuscator.getMethodDescriptor(listUpdateItems));
            XposedBridge.hookMethod(listUpdateItems, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var results = ReflectionUtils.findArrayOfType(param.args, List.class);
                    if (results.isEmpty()) return;
                    var list = (List<?>) results.get(0).second;
                    var arrList = new ArrayList<>(list);
                    removeItems(arrList, channels, removechannelRec, headerChannelItem, listChannelItem, removeChannelRecClass);
                    param.args[results.get(0).first] = arrList;
                }
            });

            XposedBridge.hookAllConstructors(removeChannelRecClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    var pairs = ReflectionUtils.findArrayOfType(param.args, List.class);
                    for (var pair : pairs) {
                        param.args[pair.first] = new ArrayList<>();
                    }
                }
            });

            if (channels) {
                XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", classLoader, "onPrepareOptionsMenu", Menu.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        var menu = (Menu) param.args[0];
                        var id = Utils.getID("menuitem_create_newsletter", "id");
                        var menuItem = menu.findItem(id);
                        if (menuItem != null) {
                            menuItem.setVisible(false);
                        }
                    }
                });
            }
        }

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Channels";
    }
}
