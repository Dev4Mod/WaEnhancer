package com.wmods.wppenhacer.xposed

import android.content.pm.PackageInstaller
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.IOException

object AntiUpdater {
    fun hookSession(lpparam: LoadPackageParam) {
        if (lpparam.packageName == "android") return
        XposedBridge.hookAllMethods(
            PackageInstaller::class.java,
            "createSession",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val session = param.args[0] as PackageInstaller.SessionParams?
                    val packageName = XposedHelpers.getObjectField(session, "mPackageName")
                    if (packageName == FeatureLoader.PACKAGE_WPP || packageName == FeatureLoader.PACKAGE_BUSINESS) {
                        param.setThrowable(IOException("UPDATE LOCKED BY WAENHANCER"))
                    }
                }
            })
    }
}
