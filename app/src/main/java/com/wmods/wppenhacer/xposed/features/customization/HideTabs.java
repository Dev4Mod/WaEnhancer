package com.wmods.wppenhacer.xposed.features.customization;

import static com.wmods.wppenhacer.xposed.features.customization.SeparateGroup.tabs;

import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.util.ArrayList;
import java.util.List;

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
        if (hidetabs == null || hidetabs.isEmpty())
            return;



        var home = XposedHelpers.findClass("com.whatsapp.HomeActivity", loader);


        var hideTabsList = new ArrayList<>(hidetabs);

        var OnTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(loader);

        XposedBridge.hookMethod(OnTabItemAddMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (MenuItem) param.getResult();
                var menuItemId = menu.getItemId();
                if (hideTabsList.contains(String.valueOf(menuItemId))) {
                    menu.setVisible(false);
                }
            }
        });

        var loadTabFrameClass = Unobfuscator.loadTabFrameClass(loader);
        logDebug(loadTabFrameClass);

        XposedBridge.hookAllMethods(FrameLayout.class, "onMeasure", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return;
                for (var item : hideTabsList) {
                    View view;
                    if ((view = ((View) param.thisObject).findViewById(Integer.parseInt(item))) != null) {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        });

        var onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(loader);
        var onMenuItemClick = Unobfuscator.loadOnMenuItemClickClass(loader);

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
        var tabIsHidden = hidetabs.contains(String.valueOf(tabs.get(index)));
        if (!tabIsHidden) return index;
        var idAtual = XposedHelpers.getIntField(WppCore.getMainActivity(), "A03");
        var indexAtual = tabs.indexOf(idAtual);
        var newIndex = index > indexAtual ? index + 1 : index - 1;
        if (newIndex < 0) return 0;
        if (newIndex >= tabs.size()) return indexAtual;
        return getNewTabIndex(hidetabs, newIndex);
    }
}
