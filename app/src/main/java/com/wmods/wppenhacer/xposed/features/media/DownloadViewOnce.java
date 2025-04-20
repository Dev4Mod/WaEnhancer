package com.wmods.wppenhacer.xposed.features.media;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
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

    private static void downloadFile(Object userJid, File file) throws Exception {
        var dest = Utils.getDestination("View Once");
        var fileExtension = file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(".") + 1);
        var name = Utils.generateName(userJid, fileExtension);
        var error = Utils.copyFile(file, dest, name);
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
            // Media Activity
            XposedBridge.hookMethod(menuMethod, new XC_MethodHook() {
                @Override
                @SuppressLint("DiscouragedApi")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var fmessageField = ReflectionUtils.getFieldByExtendType(param.thisObject.getClass(), FMessageWpp.TYPE);
                    Object fmessageObj = null;
                    if (fmessageField != null) {
                        fmessageObj = fmessageField.get(param.thisObject);
                    }
                    if (fmessageObj == null) {
                        var keyField = ReflectionUtils.getFieldByExtendType(param.thisObject.getClass(), FMessageWpp.Key.TYPE);
                        if (keyField != null) {
                            var keyObj = keyField.get(param.thisObject);
                            fmessageObj = WppCore.getFMessageFromKey(keyObj);
                        }
                    }
                    FMessageWpp fMessage = new FMessageWpp(fmessageObj);

                    // check media is view once
                    if (!fMessage.isViewOnce()) return;
                    Menu menu = (Menu) param.args[0];
                    MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(ResId.drawable.download);
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    item.setOnMenuItemClickListener(item1 -> {
                        try {
                            var file = fMessage.getMediaFile();
                            downloadFile(fMessage.getKey().remoteJid, file);
                        } catch (Exception e) {
                            Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                        }
                        return true;
                    });
                }


            });
            // View Once Activity
            XposedHelpers.findAndHookMethod(WppCore.getViewOnceViewerActivityClass(classLoader), "onCreateOptionsMenu", classLoader.loadClass("android.view.Menu"),
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Menu menu = (Menu) param.args[0];
                            MenuItem item = menu.add(0, 0, 0, ResId.string.download).setIcon(ResId.drawable.download);
                            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                            item.setOnMenuItemClickListener(item1 -> {
                                CompletableFuture.runAsync(() -> {
                                    var keyClass = FMessageWpp.Key.TYPE;
                                    var fieldType = ReflectionUtils.getFieldByType(param.thisObject.getClass(), keyClass);
                                    var keyMessageObj = ReflectionUtils.getObjectField(fieldType, param.thisObject);
                                    var fmessageObj = WppCore.getFMessageFromKey(keyMessageObj);
                                    var fmessage = new FMessageWpp(fmessageObj);
                                    var file = fmessage.getMediaFile();
                                    var userJid = fmessage.getKey().remoteJid;
                                    try {
                                        downloadFile(userJid, file);
                                    } catch (Exception e) {
                                        Utils.showToast(e.getMessage(), Toast.LENGTH_LONG);
                                    }
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
