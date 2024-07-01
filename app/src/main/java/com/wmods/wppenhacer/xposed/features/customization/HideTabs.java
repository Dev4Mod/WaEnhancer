package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.xposed.features.customization.SeparateGroup.tabs;

import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideTabs extends Feature {
    public HideTabs(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var hidetabs = prefs.getStringSet("hidetabs", null);
        var igstatus = prefs.getBoolean("igstatus", false);
        if (hidetabs == null || hidetabs.isEmpty())
            return;

        var home = XposedHelpers.findClass("com.whatsapp.HomeActivity", classLoader);

        var hideTabsList = hidetabs.stream().map(Integer::valueOf).collect(Collectors.toList());

        var onCreateTabList = Unobfuscator.loadTabListMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList));
        var ListField = Unobfuscator.getFieldByType(home, List.class);

        XposedBridge.hookMethod(onCreateTabList, new XC_MethodHook() {
            @Override
            @SuppressWarnings("unchecked")
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var list = (List<Integer>) XposedHelpers.getStaticObjectField(home, ListField.getName());
                for (var item : hideTabsList) {
                    if (item != SeparateGroup.STATUS || !igstatus) {
                        list.remove(item);
                    }
                }
            }
        });

        var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader);
        XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menuItem = (MenuItem) param.getResult();
                var menuItemId = menuItem.getItemId();
                if (hideTabsList.contains(menuItemId)) {
                    menuItem.setVisible(false);
                }
            }
        });

        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader);
        logDebug(loadTabFrameClass);

        XposedBridge.hookAllMethods(FrameLayout.class, "onMeasure", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return;
                if (tabs != null) {
                    var arr = new ArrayList<>(tabs);
                    arr.removeAll(hideTabsList);
                    if (arr.size() == 1) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
                for (var item : hideTabsList) {
                    View view;
                    if ((view = ((View) param.thisObject).findViewById(item)) != null) {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        });

        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader);
        var onMenuItemClick = Unobfuscator.loadOnMenuItemClickClass(classLoader);

        XposedBridge.hookMethod(onMenuItemSelected, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!Unobfuscator.isCalledFromClass(home) && !Unobfuscator.isCalledFromClass(onMenuItemClick))
                    return;
                var index = (int) param.args[0];
                param.args[0] = getNewTabIndex(hideTabsList, index);
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Tabs";
    }

    public int getNewTabIndex(List hidetabs, int index) {
        if (tabs == null) return index;
        var tabIsHidden = hidetabs.contains(tabs.get(index));
        if (!tabIsHidden) return index;
        var idAtual = XposedHelpers.getIntField(WppCore.getCurrentActivity(), "A03");
        var indexAtual = tabs.indexOf(idAtual);
        var newIndex = index > indexAtual ? index + 1 : index - 1;
        if (newIndex < 0) return 0;
        if (newIndex >= tabs.size()) return indexAtual;
        return getNewTabIndex(hidetabs, newIndex);
    }
}
