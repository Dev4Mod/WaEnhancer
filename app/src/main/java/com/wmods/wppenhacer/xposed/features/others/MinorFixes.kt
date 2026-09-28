package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentProvider
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import com.wmods.wppenhacer.xposed.core.Feature
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class MinorFixes(classLoader: ClassLoader, prefs: SharedPreferences) : Feature(classLoader, prefs) {
    private val mlKitInitLock = Any()
    private var mlKitInitProviderHandled = false

    override fun doHook() {
        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    if (activity.javaClass.name != DOCUMENT_PICKER_ACTIVITY) return
                    ensureMlKitInitialized(activity)
                }
            })
    }

    private fun ensureMlKitInitialized(activity: Activity) {
        synchronized(mlKitInitLock) {
            if (mlKitInitProviderHandled) return

            try {
                val providerClass = Class.forName(
                    ML_KIT_INIT_PROVIDER,
                    true,
                    activity.classLoader
                )
                val provider = providerClass.getDeclaredConstructor()
                    .newInstance() as ContentProvider
                val providerInfo = activity.packageManager.getProviderInfo(
                    ComponentName(activity.packageName, ML_KIT_INIT_PROVIDER),
                    PackageManager.GET_META_DATA
                )

                provider.attachInfo(activity.applicationContext, providerInfo)
                mlKitInitProviderHandled = true
                log("Initialized MlKitInitProvider before DocumentPickerActivity")
            } catch (error: Throwable) {
                val alreadyInitialized = generateSequence(error) { it.cause }
                    .filterIsInstance<IllegalStateException>()
                    .any { it.message?.contains("MlKitContext is already initialized") == true }

                if (alreadyInitialized) {
                    mlKitInitProviderHandled = true
                    return
                }

                log("Failed to initialize MlKitInitProvider before DocumentPickerActivity")
                log(error)
            }
        }
    }

    private companion object {
        const val DOCUMENT_PICKER_ACTIVITY = "com.whatsapp.documentpicker.DocumentPickerActivity"
        const val ML_KIT_INIT_PROVIDER = "com.google.mlkit.common.internal.MlKitInitProvider"
    }

    override fun getPluginName(): String = "Minor Fixes"
}
