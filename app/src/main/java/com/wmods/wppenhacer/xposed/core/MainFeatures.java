package com.wmods.wppenhacer.xposed.core;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.features.customization.BubbleColors;
import com.wmods.wppenhacer.xposed.features.customization.CustomTheme;
import com.wmods.wppenhacer.xposed.features.customization.CustomTime;
import com.wmods.wppenhacer.xposed.features.customization.CustomToolbar;
import com.wmods.wppenhacer.xposed.features.customization.CustomView;
import com.wmods.wppenhacer.xposed.features.customization.DotOnline;
import com.wmods.wppenhacer.xposed.features.customization.FilterGroups;
import com.wmods.wppenhacer.xposed.features.customization.HideTabs;
import com.wmods.wppenhacer.xposed.features.customization.IGStatus;
import com.wmods.wppenhacer.xposed.features.customization.SeparateGroup;
import com.wmods.wppenhacer.xposed.features.general.AntiRevoke;
import com.wmods.wppenhacer.xposed.features.general.CallPrivacy;
import com.wmods.wppenhacer.xposed.features.general.CallType;
import com.wmods.wppenhacer.xposed.features.general.ChatLimit;
import com.wmods.wppenhacer.xposed.features.general.DndMode;
import com.wmods.wppenhacer.xposed.features.general.MediaPreview;
import com.wmods.wppenhacer.xposed.features.general.MediaQuality;
import com.wmods.wppenhacer.xposed.features.general.NewChat;
import com.wmods.wppenhacer.xposed.features.general.Others;
import com.wmods.wppenhacer.xposed.features.general.PinnedLimit;
import com.wmods.wppenhacer.xposed.features.general.SeenTick;
import com.wmods.wppenhacer.xposed.features.general.ShareLimit;
import com.wmods.wppenhacer.xposed.features.general.ShowEditMessage;
import com.wmods.wppenhacer.xposed.features.general.StatusDownload;
import com.wmods.wppenhacer.xposed.features.general.ViewOnce;
import com.wmods.wppenhacer.xposed.features.privacy.FreezeLastSeen;
import com.wmods.wppenhacer.xposed.features.privacy.GhostMode;
import com.wmods.wppenhacer.xposed.features.privacy.HideArchive;
import com.wmods.wppenhacer.xposed.features.privacy.HideReceipt;
import com.wmods.wppenhacer.xposed.features.privacy.HideSeen;
import com.wmods.wppenhacer.xposed.features.privacy.HideTagForward;

import java.util.ArrayList;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class MainFeatures {
    public static Application mApp;

    public final static String PACKAGE_WPP = "com.whatsapp";
    public final static String PACKAGE_BUSINESS = "com.whatsapp.w4b";

    private static final ArrayList<ErrorItem> list = new ArrayList<>();

    public static void start(@NonNull ClassLoader loader, @NonNull XSharedPreferences pref, String sourceDir) {

        if (!Unobfuscator.initDexKit(sourceDir)) {
            XposedBridge.log("Can't init dexkit");
            return;
        }
        Feature.DEBUG = pref.getBoolean("enablelogs", true);

        XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @SuppressWarnings("deprecation")
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                mApp = (Application) param.args[0];
                PackageManager packageManager = mApp.getPackageManager();
                pref.registerOnSharedPreferenceChangeListener((sharedPreferences, s) -> pref.reload());
                PackageInfo packageInfo = packageManager.getPackageInfo(mApp.getPackageName(), 0);
                XposedBridge.log(packageInfo.versionName);
                try {
                    DesignUtils.setPrefs(pref);
                    UnobfuscatorCache.init(mApp, pref);
                    WppDatabase.Initialize(loader, pref);
                    WppCore.Initialize(loader);
                    initComponents(loader, pref);
                    plugins(loader, pref, packageInfo.versionName);
                    registerReceivers();
                    mApp.registerActivityLifecycleCallbacks(new WaCallback());
                    sendEnabledBroadcast(mApp);
                    if (Feature.DEBUG)
                        XposedHelpers.setStaticIntField(XposedHelpers.findClass("com.whatsapp.util.Log", loader), "level", 5);
                } catch (Exception e) {
                    XposedBridge.log(e);
                    var error = new ErrorItem();
                    error.setPluginName("MainFeatures[Critical]");
                    error.setWhatsAppVersion(packageInfo.versionName);
                    error.setModuleVersion(BuildConfig.VERSION_NAME);
                    error.setError(e.getMessage() + ": " + Arrays.toString(Arrays.stream(e.getStackTrace()).filter(s -> !s.getClassName().startsWith("android") && !s.getClassName().startsWith("com.android")).map(StackTraceElement::toString).toArray()));
                    list.add(error);
                }
            }
        });

        XposedHelpers.findAndHookMethod("com.whatsapp.HomeActivity", loader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (!list.isEmpty()) {
                    var activity = (Activity) param.thisObject;
                    new AlertDialogWpp(activity)
                            .setTitle(activity.getString(ResId.string.error_detected))
                            .setMessage(activity.getString(ResId.string.version_error) + String.join("\n", list.stream().map(ErrorItem::getPluginName).toArray(String[]::new)))
                            .setPositiveButton(activity.getString(ResId.string.copy_to_clipboard), (dialog, which) -> {
                                var clipboard = (ClipboardManager) mApp.getSystemService(Context.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("text", String.join("\n", list.stream().map(ErrorItem::toString).toArray(String[]::new)));
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(mApp, ResId.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .show();
                }
            }
        });
    }

    private static void initComponents(ClassLoader loader, XSharedPreferences pref) {
        AlertDialogWpp.initDialog(loader);
    }

    private static void registerReceivers() {
        // Reboot receiver
        BroadcastReceiver restartReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (context.getPackageName().equals(intent.getStringExtra("PKG"))) {
                    var appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
                    Toast.makeText(context, context.getString(ResId.string.rebooting) + " " + appName + "...", Toast.LENGTH_SHORT).show();
                    if (!Utils.doRestart(context))
                        Toast.makeText(context, "Unable to rebooting " + appName, Toast.LENGTH_SHORT).show();
                }
            }
        };
        ContextCompat.registerReceiver(mApp, restartReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".WHATSAPP.RESTART"), ContextCompat.RECEIVER_EXPORTED);

        /// Wpp receiver
        BroadcastReceiver wppReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendEnabledBroadcast(context);
            }
        };
        ContextCompat.registerReceiver(mApp, wppReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".CHECK_WPP"), ContextCompat.RECEIVER_EXPORTED);

        // Dialog receiver restart
        BroadcastReceiver restartManualReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WppCore.setPrivBoolean("need_restart", true);
            }
        };
        ContextCompat.registerReceiver(mApp, restartManualReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".MANUAL_RESTART"), ContextCompat.RECEIVER_EXPORTED);
    }

    private static void sendEnabledBroadcast(Context context) {
        try {
            Intent wppIntent = new Intent(BuildConfig.APPLICATION_ID + ".RECEIVER_WPP");
            wppIntent.putExtra("VERSION", context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
            wppIntent.putExtra("PKG", context.getPackageName());
            wppIntent.setPackage(BuildConfig.APPLICATION_ID);
            context.sendBroadcast(wppIntent);
        } catch (Exception ignored) {
        }
    }

    private static void plugins(@NonNull ClassLoader loader, @NonNull XSharedPreferences pref, @NonNull String versionWpp) {

        var classes = new Class<?>[]{
                ShowEditMessage.class,
                AntiRevoke.class,
                CustomToolbar.class,
                CustomView.class,
                SeenTick.class,
                BubbleColors.class,
                CallPrivacy.class,
                CustomTheme.class,
                ChatLimit.class,
                SeparateGroup.class,
                DotOnline.class,
                DndMode.class,
                FreezeLastSeen.class,
                GhostMode.class,
                HideArchive.class,
                HideReceipt.class,
                HideSeen.class,
                HideTagForward.class,
                HideTabs.class,
                IGStatus.class,
                MediaQuality.class,
                NewChat.class,
                Others.class,
                PinnedLimit.class,
                CustomTime.class,
                ShareLimit.class,
                StatusDownload.class,
                ViewOnce.class,
                CallType.class,
                MediaPreview.class,
                FilterGroups.class
        };

        for (var classe : classes) {
            try {
                var constructor = classe.getConstructor(ClassLoader.class, XSharedPreferences.class);
                var plugin = (Feature) constructor.newInstance(loader, pref);
                plugin.doHook();
            } catch (Throwable e) {
                XposedBridge.log(e);
                var error = new ErrorItem();
                error.setPluginName(classe.getSimpleName());
                error.setWhatsAppVersion(versionWpp);
                error.setModuleVersion(BuildConfig.VERSION_NAME);
                error.setError(e.getMessage() + ": " + Arrays.toString(Arrays.stream(e.getStackTrace()).filter(s -> !s.getClassName().startsWith("android") && !s.getClassName().startsWith("com.android")).map(StackTraceElement::toString).toArray()));
                list.add(error);
            }
        }
    }


    private static class ErrorItem {
        private String pluginName;
        private String whatsAppVersion;
        private String error;
        private String moduleVersion;

        @NonNull
        @Override
        public String toString() {
            return "pluginName='" + getPluginName() + '\'' +
                    "\nmoduleVersion='" + getModuleVersion() + '\'' +
                    "\nwhatsAppVersion='" + getWhatsAppVersion() + '\'' +
                    "\nerror='" + getError() + '\'';
        }

        public String getWhatsAppVersion() {
            return whatsAppVersion;
        }

        public void setWhatsAppVersion(String whatsAppVersion) {
            this.whatsAppVersion = whatsAppVersion;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getPluginName() {
            return pluginName;
        }

        public void setPluginName(String pluginName) {
            this.pluginName = pluginName;
        }

        public String getModuleVersion() {
            return moduleVersion;
        }

        public void setModuleVersion(String moduleVersion) {
            this.moduleVersion = moduleVersion;
        }
    }
}
