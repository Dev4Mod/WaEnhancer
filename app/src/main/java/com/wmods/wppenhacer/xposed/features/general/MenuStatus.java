package com.wmods.wppenhacer.xposed.features.general;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class MenuStatus extends Feature {

    public static HashSet<MenuItemStatus> menuStatuses = new HashSet<>();

    public MenuStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var mediaClass = Unobfuscator.loadStatusDownloadMediaClass(classLoader);
        logDebug("Media class: " + mediaClass.getName());
        var menuStatusClass = Unobfuscator.loadMenuStatusClass(classLoader);
        logDebug("MenuStatus class: " + menuStatusClass.getName());
        var fieldFile = Unobfuscator.loadStatusDownloadFileField(classLoader);
        logDebug("File field: " + fieldFile.getName());
        var clazzSubMenu = Unobfuscator.loadStatusDownloadSubMenuClass(classLoader);
        logDebug("SubMenu class: " + clazzSubMenu.getName());
        var clazzMenu = Unobfuscator.loadStatusDownloadMenuClass(classLoader);
        logDebug("Menu class: " + clazzMenu.getName());
        var menuField = Unobfuscator.getFieldByType(clazzSubMenu, clazzMenu);
        logDebug("Menu field: " + menuField.getName());

        Class<?> StatusPlaybackBaseFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        Class<?> StatusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        var listStatusField = ReflectionUtils.getFieldsByExtendType(StatusPlaybackContactFragmentClass, List.class).get(0);

        XposedHelpers.findAndHookMethod(menuStatusClass, "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var clazz = param.thisObject.getClass();
                Field subMenuField = ReflectionUtils.findFieldUsingFilter(clazz, f -> f.getType() == Object.class && clazzSubMenu.isInstance(ReflectionUtils.getField(f, param.thisObject)));
                Object subMenu = ReflectionUtils.getField(subMenuField, param.thisObject);
                var fragment = ReflectionUtils.findFieldUsingFilter(clazz, f -> StatusPlaybackBaseFragmentClass.isInstance(ReflectionUtils.getField(f, param.thisObject)));
                if (fragment == null) {
                    logDebug("Fragment not found");
                    return;
                }
                var fragmentInstance = fragment.get(param.thisObject);
                var index = (int) XposedHelpers.getObjectField(fragmentInstance, "A00");
                var listStatus = (List) listStatusField.get(fragmentInstance);
                var fMessage = new FMessageWpp(listStatus.get(index));
                var menu = (Menu) ReflectionUtils.getField(menuField, subMenu);
                for (MenuItemStatus menuStatus : menuStatuses) {
                    var menuItem = menuStatus.addMenu(menu);
                    if (menuItem == null) continue;
                    menuItem.setOnMenuItemClickListener(item -> {
                        menuStatus.onClick(item, fragmentInstance, fMessage);
                        return true;
                    });
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Status";
    }

    public abstract static class MenuItemStatus {

        public abstract MenuItem addMenu(Menu menu);

        public abstract void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp);
    }
}
