package com.wmods.wppenhacer.xposed.features.others

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentProvider
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature

class MinorFixes(classLoader: ClassLoader, xprefs: SharedPreferences) :
    Feature(classLoader, xprefs) {
    private val mlKitInitLock = Any()
    private var mlKitInitProviderHandled = false

    override fun doHook() {
        hookMlKitDiscoveryMetadata()
        Instrumentation::class.java.resolve().firstMethod {
            name = "callActivityOnCreate"
            parameters(Activity::class.java, Bundle::class.java)
        }.hook {
            before {
                val activity = args[0] as? Activity ?: return@before
                if (activity.javaClass.name != DOCUMENT_PICKER_ACTIVITY) return@before
                log("DocumentPickerActivity.onCreate intercepted, ML Kit handled=$mlKitInitProviderHandled")
                ensureMlKitInitialized(activity)
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun hookMlKitDiscoveryMetadata() {
        runCatching {
            val pmClass = Class.forName("android.app.ApplicationPackageManager")
            pmClass.resolve().firstMethod {
                name = "getServiceInfo"
                parameters(ComponentName::class.java, Int::class.javaPrimitiveType!!)
            }.hook {
                after {
                    val component = args[0] as? ComponentName ?: return@after
                    if (component.className != ML_KIT_DISCOVERY_SERVICE) return@after
                    val info = result as? ServiceInfo ?: return@after
                    val meta = info.metaData ?: Bundle().also { info.metaData = it }
                    if (meta.keySet().none { it.startsWith(ML_KIT_REGISTRAR_PREFIX) }) {
                        meta.putString(
                            ML_KIT_REGISTRAR_PREFIX + ML_KIT_COMMON_REGISTRAR,
                            "com.google.firebase.components.ComponentRegistrar"
                        )
                        log("Restored missing ML Kit registrar metadata")
                    }
                }
            }
        }.onFailure { log(it) }
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
                val providerInfo = runCatching {
                    activity.packageManager.getProviderInfo(
                        ComponentName(activity.packageName, ML_KIT_INIT_PROVIDER),
                        PackageManager.GET_META_DATA
                    )
                }.getOrElse {
                    log("getProviderInfo failed, using a synthetic ProviderInfo")
                    ProviderInfo().apply {
                        name = ML_KIT_INIT_PROVIDER
                        packageName = activity.packageName
                        authority = "${activity.packageName}.mlkitinitprovider"
                        applicationInfo = activity.applicationInfo
                    }
                }

                provider.attachInfo(activity.applicationContext, providerInfo)
                mlKitInitProviderHandled = true
                log("Initialized MlKitInitProvider before DocumentPickerActivity")
            } catch (error: Throwable) {
                val alreadyInitialized = generateSequence(error) { it.cause }
                    .filterIsInstance<IllegalStateException>()
                    .any { it.message?.contains("MlKitContext is already initialized") == true }

                if (alreadyInitialized) {
                    log("MlKitContext was already initialized")
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
        const val ML_KIT_DISCOVERY_SERVICE =
            "com.google.mlkit.common.internal.MlKitComponentDiscoveryService"
        const val ML_KIT_REGISTRAR_PREFIX = "com.google.firebase.components:"
        const val ML_KIT_COMMON_REGISTRAR =
            "com.google.mlkit.common.internal.CommonComponentRegistrar"
    }

    override fun getPluginName(): String = "Minor Fixes"
}
