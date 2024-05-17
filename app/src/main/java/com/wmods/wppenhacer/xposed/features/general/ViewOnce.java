package com.wmods.wppenhacer.xposed.features.general;


import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.ResId;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.Utils;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ViewOnce extends Feature {
    private boolean isFromMe;

    public ViewOnce(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var methods = Unobfuscator.loadViewOnceMethod(loader);
        var classViewOnce = Unobfuscator.loadViewOnceClass(loader);
        logDebug(classViewOnce);
        var viewOnceStoreMethod = Unobfuscator.loadViewOnceStoreMethod(loader);
        logDebug(Unobfuscator.getMethodDescriptor(viewOnceStoreMethod));
        var messageKeyField = Unobfuscator.loadMessageKeyField(loader);

        XposedBridge.hookMethod(viewOnceStoreMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getBoolean("viewonce", false)) return;
                isFromMe = false;
                var messageObject = param.args[0];
                if (messageObject == null) return;
                var messageKey = messageKeyField.get(messageObject);
                isFromMe = XposedHelpers.getBooleanField(messageKey, "A02");
            }
        });

        for (var method : methods) {
            logDebug(Unobfuscator.getMethodDescriptor(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!prefs.getBoolean("viewonce", false)) return;
                    if ((int) param.getResult() != 2 && (Unobfuscator.isCalledFromClass(classViewOnce))) {
                        param.setResult(0);
                    } else if ((int) param.getResult() != 2 && !isFromMe && (Unobfuscator.isCalledFromClass(viewOnceStoreMethod.getDeclaringClass()))) {
                        param.setResult(0);
                    }
                }
            });
        }

        if (prefs.getBoolean("downloadviewonce", false)) {

            var menuMethod = Unobfuscator.loadViewOnceDownloadMenuMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(menuMethod));
            var menuIntField = Unobfuscator.loadViewOnceDownloadMenuField(loader);
            logDebug(Unobfuscator.getFieldDescriptor(menuIntField));
            var initIntField = Unobfuscator.loadViewOnceDownloadMenuField2(loader);
            logDebug(Unobfuscator.getFieldDescriptor(initIntField));
            var callMethod = Unobfuscator.loadViewOnceDownloadMenuCallMethod(loader);
            logDebug(Unobfuscator.getMethodDescriptor(callMethod));
            var fileField = Unobfuscator.loadStatusDownloadFileField(loader);
            logDebug(Unobfuscator.getFieldDescriptor(fileField));

            XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
                @Override
                @SuppressLint("DiscouragedApi")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);

                    if (XposedHelpers.getIntField(param.thisObject, menuIntField.getName()) == 3) {
                        Menu menu = (Menu) param.args[0];
                        MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(Utils.getID("btn_download", "drawable"));
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                        item.setOnMenuItemClickListener(item1 -> {
                            var i = XposedHelpers.getIntField(param.thisObject, initIntField.getName());
                            var message = callMethod.getParameterCount() == 2 ? XposedHelpers.callMethod(param.thisObject, callMethod.getName(), param.thisObject, i) : XposedHelpers.callMethod(param.thisObject, callMethod.getName(), i);
                            if (message != null) {
                                var fileData = XposedHelpers.getObjectField(message, "A01");
                                var file = (File) XposedHelpers.getObjectField(fileData, fileField.getName());
                                var dest = Utils.getDestination(prefs, file, "View Once");
                                if (Utils.copyFile(file,new File(dest))) {
                                    Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.saved_to) + dest, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(Utils.getApplication(), Utils.getApplication().getString(ResId.string.error_when_saving_try_again), Toast.LENGTH_SHORT).show();
                                }
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
        return "View Once";
    }



}