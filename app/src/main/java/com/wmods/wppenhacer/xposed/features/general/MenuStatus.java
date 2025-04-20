package com.wmods.wppenhacer.xposed.features.general;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MenuStatus extends Feature {

    public static HashSet<MenuItemStatus> menuStatuses = new HashSet<>();

    public MenuStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

        var menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader);
        logDebug("MenuStatus method: " + menuStatusMethod.getName());
        var menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader);

        Class<?> StatusPlaybackBaseFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        Class<?> StatusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        var listStatusField = ReflectionUtils.getFieldsByExtendType(StatusPlaybackContactFragmentClass, List.class).get(0);

        XposedBridge.hookMethod(menuStatusMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var fieldObjects = Arrays.stream(param.method.getDeclaringClass().getDeclaredFields()).map(field -> ReflectionUtils.getObjectField(field, param.thisObject)).filter(Objects::nonNull).collect(Collectors.toList());

                Object fragmentInstance;
                if (param.thisObject != null && StatusPlaybackContactFragmentClass.isInstance(param.thisObject)) {
                    fragmentInstance = param.thisObject;
                } else {
                    fragmentInstance = fieldObjects.stream().filter(StatusPlaybackBaseFragmentClass::isInstance).findFirst().orElse(null);
                }
                Menu menu;
                if (param.args.length > 0 && param.args[0] instanceof Menu) {
                    menu = (Menu) param.args[0];
                } else {
                    var menuManager = fieldObjects.stream().filter(menuManagerClass::isInstance).findFirst().orElse(null);
                    var menuField = ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu.class);
                    menu = (Menu) ReflectionUtils.getObjectField(menuField, menuManager);
                }

                var index = (int) XposedHelpers.getObjectField(fragmentInstance, "A00");
                var listStatus = (List) listStatusField.get(fragmentInstance);
                var object = listStatus.get(index);
                if (object == null) return;
                if (!FMessageWpp.TYPE.isInstance(object)) {
                    var fMessageField = ReflectionUtils.getFieldByExtendType(object.getClass(), FMessageWpp.TYPE);
                    object = ReflectionUtils.getObjectField(fMessageField, object);
                }

                var fMessage = new FMessageWpp(object);

                for (MenuItemStatus menuStatus : menuStatuses) {
                    var menuItem = menuStatus.addMenu(menu, fMessage);
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

        public abstract MenuItem addMenu(Menu menu, FMessageWpp fMessage);

        public abstract void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp);
    }
}
