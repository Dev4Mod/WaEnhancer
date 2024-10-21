package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DownloadViewOnce extends Feature {
    public DownloadViewOnce(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
    }

    private static void downloadFile(Object userJid, File file) {
        var dest = Utils.getDestination("View Once");
        var fileExtension = file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(".") + 1);
        var name = Utils.generateName(userJid, fileExtension);
        var error = Utils.copyFile(file, new File(dest, name));
        if (TextUtils.isEmpty(error)) {
            Utils.showToast(Utils.getApplication().getString(ResId.string.saved_to) + dest, Toast.LENGTH_LONG);
        } else {
            Utils.showToast(Utils.getApplication().getString(ResId.string.error_when_saving_try_again) + ":" + error, Toast.LENGTH_LONG);
        }
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

            // Media Activity
            XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
                @Override
                @SuppressLint("DiscouragedApi")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var id = XposedHelpers.getIntField(param.thisObject, menuIntField.getName());
                    XposedBridge.log("ID: " + id);
                    if (id == 3 || id == 0) {
                        Menu menu = (Menu) param.args[0];
                        MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(ResId.drawable.download);
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                        item.setOnMenuItemClickListener(item1 -> {
                            try {
                                var i = XposedHelpers.getIntField(param.thisObject, initIntField.getName());
                                var message = callMethod.getParameterCount() == 2 ? XposedHelpers.callMethod(param.thisObject, callMethod.getName(), param.thisObject, i) : XposedHelpers.callMethod(param.thisObject, callMethod.getName(), i);
                                if (message != null) {
                                    var fileData = XposedHelpers.getObjectField(message, "A01");
                                    var file = (File) ReflectionUtils.getField(fileField, fileData);
                                    var userJid = new FMessageWpp(message).getKey().remoteJid;
                                    downloadFile(userJid, file);
                                }
                            } catch (Exception e) {
                                Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                            }
                            return true;
                        });
                    }

                }
            });
            // View Once Activity
            XposedHelpers.findAndHookMethod("com.whatsapp.messaging.ViewOnceViewerActivity", classLoader, "onCreateOptionsMenu", classLoader.loadClass("android.view.Menu"),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Menu menu = (Menu) param.args[0];
                            MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(ResId.drawable.download);
                            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                            item.setOnMenuItemClickListener(item1 -> {
                                CompletableFuture.runAsync(() -> {
                                    var messageStore = MessageStore.getInstance();
                                    var keyClass = FMessageWpp.Key.TYPE;
                                    var fieldType = ReflectionUtils.getFieldByType(param.thisObject.getClass(), keyClass);
                                    var keyMessage = new FMessageWpp.Key(ReflectionUtils.getField(fieldType, param.thisObject));
                                    var id = messageStore.getIdfromKey(keyMessage.messageID);
                                    var userJid = keyMessage.remoteJid;
                                    var filePath = messageStore.getMediaFromID(id);
                                    downloadFile(userJid, new File(filePath));
                                });
                                return true;
                            });

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
