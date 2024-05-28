package com.wmods.wppenhacer;

import android.annotation.SuppressLint;
import android.content.ContextWrapper;
import android.content.res.XModuleResources;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.wmods.wppenhacer.xposed.AntiUpdater;
import com.wmods.wppenhacer.xposed.core.MainFeatures;
import com.wmods.wppenhacer.xposed.core.ResId;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WppXposed implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {

    private static XSharedPreferences pref;
    private String MODULE_PATH;
    public static XC_InitPackageResources.InitPackageResourcesParam ResParam;

    @NonNull
    public static XSharedPreferences getPref() {
        if (pref == null) {
            pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID + "_preferences");
            pref.makeWorldReadable();
            pref.reload();
        }
        return pref;
    }

    @SuppressLint("WorldReadableFiles")
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        var packageName = lpparam.packageName;
        var classLoader = lpparam.classLoader;
        var sourceDir = lpparam.appInfo.sourceDir;

        if (packageName.equals(BuildConfig.APPLICATION_ID)) {
            XposedHelpers.findAndHookMethod(MainActivity.class.getName(), lpparam.classLoader, "isXposedEnabled", XC_MethodReplacement.returnConstant(true));
            XposedHelpers.findAndHookMethod(PreferenceManager.class.getName(), lpparam.classLoader, "getDefaultSharedPreferencesMode", XC_MethodReplacement.returnConstant(ContextWrapper.MODE_WORLD_READABLE));
            return;
        }
        XposedBridge.log("[•] This package: " + lpparam.packageName);
        AntiUpdater.hookSession(pref);
        if (!packageName.equals(MainFeatures.PACKAGE_WPP) && !packageName.equals(MainFeatures.PACKAGE_BUSINESS))
            return;
        MainFeatures.start(classLoader, getPref(), sourceDir);
        disableSecureFlag();
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        var packageName = resparam.packageName;

        if (!packageName.equals(MainFeatures.PACKAGE_WPP) && !packageName.equals(MainFeatures.PACKAGE_BUSINESS))
            return;
        XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);

        for (var field : ResId.string.class.getFields()) {
            var field1 = R.string.class.getField(field.getName());
            field.set(null, resparam.res.addResource(modRes, field1.getInt(null)));
        }

        for (var field : ResId.drawable.class.getFields()) {
            var field1 = R.drawable.class.getField(field.getName());
            field.set(null, resparam.res.addResource(modRes, field1.getInt(null)));
        }

        ResParam = resparam;
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }


    public void disableSecureFlag() {
        XposedHelpers.findAndHookMethod(Window.class, "setFlags", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                param.args[1] = (int) param.args[1] & ~WindowManager.LayoutParams.FLAG_SECURE;
            }
        });

        XposedHelpers.findAndHookMethod(Window.class, "addFlags", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = (int) param.args[0] & ~WindowManager.LayoutParams.FLAG_SECURE;
                if ((int) param.args[0] == 0) {
                    param.setResult(null);
                }
            }
        });
    }

}
