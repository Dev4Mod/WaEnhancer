package com.wmods.wppenhacer.xposed.bridge

import android.content.ContentProvider
import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.core.net.toUri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.provider.PrefsProvider
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object ScopeHook : YukiBaseHooker() {

    private const val SETTINGS_PROVIDER_CLASS = "com.android.providers.settings.SettingsProvider"

    private val pmsRef = AtomicReference<Any?>(null)
    private val settingsHooked = AtomicBoolean(false)

    @Volatile
    private var getPackagesForUidMethod: Method? = null

    @Volatile
    private var scopeErrorLogged = false

    override fun onHook() {
        when (packageName) {
            // libxposed reports system_server as "system"
            "android", "system" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                hookService()
                hookScope()
                hookSettings()
            }
        }
    }

    private fun hookSettings() {
        runCatching {
            ContentProvider::class.java.resolve()
                .firstMethod {
                    name = "attachInfo"
                    parameters(Context::class.java, ProviderInfo::class.java)
                }.hook {
                    after {
                        val providerClass = instance.javaClass
                        if (providerClass.name != SETTINGS_PROVIDER_CLASS) return@after
                        if (!settingsHooked.compareAndSet(false, true)) return@after
                        hookSettingsProvider(providerClass)
                    }
                }
        }.onFailure { YLog.error(e = it) }
    }

    private fun hookSettingsProvider(providerClass: Class<*>) {
        runCatching {
            providerClass.resolve()
                .firstMethod {
                    name = "call"
                    parameters(String::class.java, String::class.java, Bundle::class.java)
                }.hook {
                    before {
                        try {
                            val method = args[0] as? String ?: return@before
                            val arg = args[1] as? String ?: return@before
                            if (method != "WaEnhancer") return@before
                            val (authority, providerMethod) = when (arg) {
                                "getHookBinder" -> "hookprovider" to "getHookBinder"
                                "getPrefs" -> "preferences" to PrefsProvider.METHOD_GET_ALL
                                else -> return@before
                            }

                            val ctx = instance<ContentProvider>().context ?: return@before
                            YLog.debug(msg = "Wa Enhancer: Trying to allow blocking")
                            runCatching {
                                Binder::class.java.getMethod("allowBlockingForCurrentThread")
                                    .invoke(null)
                            }
                            result = Utils.binderLocalScope<Bundle?> {
                                val uri =
                                    "content://${BuildConfig.APPLICATION_ID}.$authority".toUri()
                                ctx.contentResolver.call(uri, providerMethod, null, null)
                            }
                            runCatching {
                                Binder::class.java.getMethod("defaultBlockingForCurrentThread")
                                    .invoke(null)
                            }
                            YLog.debug(msg = "Wa Enhancer: Bypass Scope using Provider Settings")
                        } catch (ex: Throwable) {
                            YLog.error(e = ex)
                        }
                    }
                }
            YLog.debug(msg = "Hooked SettingsProvider call in Android System")
        }.onFailure {
            settingsHooked.set(false)
            YLog.error(e = it)
        }
    }

    private fun hookService() {
        runCatching {
            "android.os.ServiceManager".toClass().resolve()
                .firstMethodOrNull { name = "addService" }
                ?.hook {
                    before {
                        val service = args[0] as? String ?: return@before
                        if (service == "package") {
                            pmsRef.compareAndSet(null, args[1])
                        }
                    }
                }
        }.onFailure { YLog.error(e = it) }
    }

    private fun hookScope() {
        // OEMs may override shouldFilterApplication in AppsFilterImpl on API 33+
        // Thanks to frknkrc44
        val classNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf("com.android.server.pm.AppsFilterBase", "com.android.server.pm.AppsFilterImpl")
        } else {
            listOf("com.android.server.pm.AppsFilter")
        }
        var hooked = 0
        for (className in classNames) {
            runCatching {
                val clazz = className.toClass()
                if (clazz.declaredMethods.none { it.name == "shouldFilterApplication" }) return@runCatching
                val methods = clazz.resolve().method { name = "shouldFilterApplication" }
                methods.hookAll {
                    before {
                        try {
                            if (shouldAllowVisibility(args)) result = false
                        } catch (e: Throwable) {
                            if (!scopeErrorLogged) {
                                scopeErrorLogged = true
                                YLog.error(msg = "Error while hooking Android System", e = e)
                            }
                        }
                    }
                }
                hooked += methods.size
            }.onFailure { YLog.debug(msg = "Scope hook not applied on $className: ${it.message}") }
        }
        if (hooked > 0) {
            YLog.debug(msg = "Hooked visibility Scope in Android System ($hooked methods)")
        } else {
            YLog.error(msg = "Unable to hook visibility Scope in Android System")
        }
    }

    private fun shouldAllowVisibility(args: Array<Any?>): Boolean {
        if (args.size < 4) return false
        val targetApp = getPackageNameFromPackageSettings(args[args.size - 2])
        if (targetApp != BuildConfig.APPLICATION_ID) return false

        val tiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val callingUid = (if (tiramisu) args[1] else args[0]) as? Int ?: return false
        if (callingUid == Process.SYSTEM_UID) return false

        val callingApps = getPackagesForUid(if (tiramisu) args[0] else pmsRef.get(), callingUid)
            ?: return false
        return callingApps.any { it == FeatureLoader.PACKAGE_WPP || it == FeatureLoader.PACKAGE_BUSINESS }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getPackagesForUid(computer: Any?, uid: Int): Array<String>? {
        if (computer == null) return null
        return Utils.binderLocalScope {
            val method = getPackagesForUidMethod
                ?.takeIf { it.declaringClass.isInstance(computer) }
                ?: ReflectionUtils.findMethodUsingFilter(computer.javaClass) {
                    it.name == "getPackagesForUid" && it.parameterTypes.contentEquals(arrayOf(Int::class.java))
                }.also { getPackagesForUidMethod = it }
            ReflectionUtils.callMethod(method, computer, uid) as? Array<String>
        }
    }

    private fun getPackageNameFromPackageSettings(packageSettings: Any?): String? {
        if (packageSettings == null) return null
        runCatching {
            return packageSettings.javaClass.getMethod("getPackageName")
                .invoke(packageSettings) as? String
        }
        for (fieldName in arrayOf("mName", "name")) {
            runCatching {
                return ReflectionUtils.getObjectField(packageSettings, fieldName) as? String
            }
        }
        val str = packageSettings.toString()
        val startIndex = str.lastIndexOf(' ') + 1
        val endIndex = str.lastIndexOf('/')
        if (endIndex < startIndex) return null
        return str.substring(startIndex, endIndex)
    }
}
