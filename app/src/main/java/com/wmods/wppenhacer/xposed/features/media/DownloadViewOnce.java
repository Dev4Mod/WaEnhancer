package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadViewOnce extends Feature {
    public DownloadViewOnce(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (prefs.getBoolean("downloadviewonce", false)) {

            var menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(menuMethod));
            var menuIntField = Unobfuscator.loadViewOnceDownloadMenuField(classLoader);
            logDebug(Unobfuscator.getFieldDescriptor(menuIntField));
            var initIntField = Unobfuscator.loadViewOnceDownloadMenuField2(classLoader);
            logDebug(Unobfuscator.getFieldDescriptor(initIntField));
            var callMethod = Unobfuscator.loadViewOnceDownloadMenuCallMethod(classLoader);
            logDebug(Unobfuscator.getMethodDescriptor(callMethod));
            var fileField = Unobfuscator.loadStatusDownloadFileField(classLoader);
            logDebug(Unobfuscator.getFieldDescriptor(fileField));

            XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
                @Override
                @SuppressLint("DiscouragedApi")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var id = XposedHelpers.getIntField(param.thisObject, menuIntField.getName());
                    if (id == 3 || id == 0) {
                        Menu menu = (Menu) param.args[0];
                        MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(Utils.getID("btn_download", "drawable"));
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                        item.setOnMenuItemClickListener(item1 -> {
                            try {
                                var i = XposedHelpers.getIntField(param.thisObject, initIntField.getName());
                                var message = callMethod.getParameterCount() == 2 ? XposedHelpers.callMethod(param.thisObject, callMethod.getName(), param.thisObject, i) : XposedHelpers.callMethod(param.thisObject, callMethod.getName(), i);
                                if (message != null) {
                                    var fileData = XposedHelpers.getObjectField(message, "A01");
                                    var file = (File) ReflectionUtils.getField(fileField, fileData);
                                    var dest = Utils.getDestination(prefs, "View Once");
                                    var userJid = new FMessageWpp(message).getUserJid();
                                    var name = Utils.generateName(userJid, "jpg");
                                    var error = Utils.copyFile(file, new File(dest, name));
                                    if (TextUtils.isEmpty(error)) {
                                        Utils.showToast(Utils.getApplication().getString(ResId.string.saved_to) + dest, Toast.LENGTH_LONG);
                                    } else {
                                        Utils.showToast(Utils.getApplication().getString(ResId.string.error_when_saving_try_again) + ":" + error, Toast.LENGTH_LONG);
                                    }
                                }
                            } catch (Exception e) {
                                Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                            }
                            return true;
                        });
                    }

                }
            });
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download View Once";
    }
}
