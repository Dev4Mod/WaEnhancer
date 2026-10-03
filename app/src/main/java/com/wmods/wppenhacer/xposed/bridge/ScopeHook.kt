package com.wmods.wppenhacer.xposed.bridge

import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.atomic.AtomicReference

object ScopeHook : YukiBaseHooker() {

    private val pmsRef = AtomicReference<Any?>(null)
    private var scopeHookResult: YukiMemberHookCreator.MemberHookCreator.Result? = null

    override fun onHook() {
        when (packageName) {
            "android" if processName == "android" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                hookService()
                hookScope()
            }

            "com.android.providers.settings" -> hookSettings()
        }
    }

    private fun hookSettings() {
        runCatching {
            "com.android.providers.settings.SettingsProvider".toClass().resolve()
                .firstMethod {
                    name = "call"
                    parameters(String::class.java, String::class.java, Bundle::class.java)
                }.hook {
                    before {
                        try {
                            val method = args[0] as? String ?: return@before
                            val arg = args[1] as? String ?: return@before
                            if (method != "WaEnhancer" || arg != "getHookBinder") return@before

                            val ctx = instance.javaClass.getMethod("getContext")
                                .invoke(instance) as? Context ?: return@before
                            YLog.debug(msg = "Wa Enhancer: Trying to allow blocking")
                            runCatching {
                                Binder::class.java.getMethod("allowBlockingForCurrentThread")
                                    .invoke(null)
                            }
                            result = Utils.binderLocalScope<Bundle?> {
                                val uri =
                                    "content://${BuildConfig.APPLICATION_ID}.hookprovider".toUri()
                                ctx.contentResolver.call(uri, "getHookBinder", null, null)
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
        }.onFailure { YLog.error(e = it) }
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

    private fun isWppCaller(callingApps: Array<String>, targetApp: String) =
        targetApp == BuildConfig.APPLICATION_ID && callingApps.any {
            it == FeatureLoader.PACKAGE_WPP || it == FeatureLoader.PACKAGE_BUSINESS
        }

    private fun hookScope() {
        runCatching {
            scopeHookResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "com.android.server.pm.AppsFilterBase".toClass().resolve()
                    .method { name = "shouldFilterApplication" }
                    .hookAll {
                        before {
                            try {
                                val snapshot = args[0]
                                val callingUid = args[1] as Int
                                if (callingUid == 1000) return@before
                                @Suppress("UNCHECKED_CAST")
                                val callingApps = Utils.binderLocalScope<Array<String>?> {
                                    val computerClass = "com.android.server.pm.Computer".toClass()
                                    val getPackagesForUid =
                                        ReflectionUtils.findMethodUsingFilter(computerClass) { it.name == "getPackagesForUid" }
                                    ReflectionUtils.callMethod(
                                        getPackagesForUid,
                                        snapshot,
                                        callingUid
                                    ) as? Array<String>
                                } ?: return@before
                                if (isWppCaller(
                                        callingApps,
                                        getPackageNameFromPackageSettings(args[3])
                                    )
                                ) {
                                    result = false
                                }
                            } catch (e: Exception) {
                                YLog.error(msg = "Error while hooking Android System", e = e)
                                unhook()
                            }
                        }
                    }
            } else {
                "com.android.server.pm.AppsFilter".toClass().resolve()
                    .method { name = "shouldFilterApplication" }
                    .hookAll {
                        before {
                            try {
                                val pms = pmsRef.get() ?: return@before
                                val callingUid = args[0] as Int
                                if (callingUid == 1000) return@before
                                @Suppress("UNCHECKED_CAST")
                                val callingApps = Utils.binderLocalScope<Array<String>?> {
                                    val getPackagesForUid =
                                        ReflectionUtils.findMethodUsingFilter(pms.javaClass) { it.name == "getPackagesForUid" }
                                    ReflectionUtils.callMethod(
                                        getPackagesForUid,
                                        pms,
                                        callingUid
                                    ) as? Array<String>
                                } ?: return@before
                                if (isWppCaller(
                                        callingApps,
                                        getPackageNameFromPackageSettings(args[2])
                                    )
                                ) {
                                    result = false
                                }
                            } catch (e: Exception) {
                                YLog.error(msg = "Error while hooking Android System", e = e)
                                unhook()
                            }
                        }
                    }
            }
            YLog.debug(msg = "Hooked visibility Scope in Android System")
        }.onFailure { YLog.error(e = it) }
    }

    private fun unhook() {
        scopeHookResult?.remove()
    }

    private fun getPackageNameFromPackageSettings(packageSettings: Any?): String {
        val str = packageSettings.toString()
        val startIndex = str.lastIndexOf(' ') + 1
        val endIndex = str.lastIndexOf('/')
        return str.substring(startIndex, endIndex)
    }
}
