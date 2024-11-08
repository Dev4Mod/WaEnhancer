package com.wmods.wppenhacer.xposed.features.media;

import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class DownloadProfile extends Feature {

    public DownloadProfile(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var loadProfileInfoField = Unobfuscator.loadProfileInfoField(classLoader);
        XposedHelpers.findAndHookMethod("com.whatsapp.profile.ViewProfilePhoto", classLoader, "onCreateOptionsMenu", Menu.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var menu = (Menu) param.args[0];
                var item = menu.add(0, 0, 0, ResId.string.download);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                item.setIcon(ResId.drawable.download);
                item.setOnMenuItemClickListener(menuItem -> {
                    var subCls = param.thisObject.getClass().getSuperclass();
                    if (subCls == null) {
                        log(new Exception("SubClass is null"));
                        return true;
                    }
                    var field = ReflectionUtils.getFieldByType(subCls, loadProfileInfoField.getDeclaringClass());
                    var jidObj = ReflectionUtils.getObjectField(loadProfileInfoField, ReflectionUtils.getObjectField(field, param.thisObject));
                    var jid = WppCore.stripJID(WppCore.getRawString(jidObj));
                    var file = WppCore.getContactPhotoFile(jid);
                    var destPath = Utils.getDestination("Profile Photo");
                    var name = Utils.generateName(jidObj, "jpg");
                    var error = Utils.copyFile(file, new File(destPath, name));
                    if (TextUtils.isEmpty(error)) {
                        Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.saved_to) + destPath, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.error_when_saving_try_again) + " " + error, Toast.LENGTH_LONG).show();
                    }
                    return true;
                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Profile Picture";
    }
}
