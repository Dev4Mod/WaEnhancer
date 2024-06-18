package com.wmods.wppenhacer.xposed.features.general;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class DeleteStatus extends Feature {

    public static boolean bypassAntiRevoke = false;

    public DeleteStatus(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
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
        var fMessageKey = Unobfuscator.loadMessageKeyField(classLoader);
        Class<?> StatusPlaybackBaseFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment");
        Class<?> StatusPlaybackContactFragmentClass = classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment");
        var listStatusField = ReflectionUtils.getFieldsByExtendType(StatusPlaybackContactFragmentClass, List.class).get(0);
        var fragmentloader = Unobfuscator.loadFragmentLoader(classLoader);
        var showDialogStatus = Unobfuscator.loadShowDialogStatusMethod(classLoader);
        Class<?> StatusDeleteDialogFragmentClass = classLoader.loadClass("com.whatsapp.status.StatusDeleteDialogFragment");
        Field fieldBundle = ReflectionUtils.getFieldByType(fragmentloader, Bundle.class);

        XposedHelpers.findAndHookMethod(menuStatusClass, "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Field subMenuField = Arrays.stream(param.thisObject.getClass().getDeclaredFields()).filter(f -> f.getType() == Object.class && clazzSubMenu.isInstance(XposedHelpers.getObjectField(param.thisObject, f.getName()))).findFirst().orElse(null);
                Object submenu = XposedHelpers.getObjectField(param.thisObject, subMenuField.getName());
                var fragment = Arrays.stream(param.thisObject.getClass().getDeclaredFields()).filter(f -> StatusPlaybackBaseFragmentClass.isInstance(XposedHelpers.getObjectField(param.thisObject, f.getName()))).findFirst().orElse(null);
                if (fragment == null) {
                    logDebug("Fragment not found");
                    return;
                }
                var fragmentInstance = fragment.get(param.thisObject);
                var index = (int) XposedHelpers.getObjectField(fragmentInstance, "A00");
                var listStatus = (List) listStatusField.get(fragmentInstance);
                var fMessage = (Object) listStatus.get(index);
                var menu = (Menu) XposedHelpers.getObjectField(submenu, menuField.getName());
                if (menu.findItem(ResId.string.delete_for_me) != null) return;
                menu.add(0, ResId.string.delete_for_me, 0, ResId.string.delete_for_me).setOnMenuItemClickListener(item -> {
                    if (fMessage == null) return true;
                    try {
                        var status = StatusDeleteDialogFragmentClass.newInstance();
                        var key = fMessageKey.get(fMessage);
                        var bundle = getBundle(key);
                        bypassAntiRevoke = true;
                        fieldBundle.set(status, bundle);
                        showDialogStatus.invoke(status, status, fragmentInstance);
                    } catch (Exception e) {
                        log(e);
                    }
                    return true;
                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Delete Status";
    }

    @NonNull
    private static Bundle getBundle(Object key) {
        var id = XposedHelpers.getObjectField(key, "A01");
        var isFromMe = XposedHelpers.getBooleanField(key, "A02");
        var remoteJid = XposedHelpers.getObjectField(key, "A00");
        var bundle = new Bundle();
        bundle.putString("fMessageKeyJid", WppCore.getRawString(remoteJid));
        bundle.putBoolean("fMessageKeyFromMe", isFromMe);
        bundle.putString("fMessageKeyId", (String) id);
        return bundle;
    }
}
