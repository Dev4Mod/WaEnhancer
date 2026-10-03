package com.wmods.wppenhacer

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.injectModuleAppResources
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.wmods.wppenhacer.xposed.AntiUpdater
import com.wmods.wppenhacer.xposed.bridge.ScopeHook
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.patch.GlobalResourceHooker
import com.wmods.wppenhacer.xposed.downgrade.Patch
import com.wmods.wppenhacer.xposed.utils.YukiSharedPreference

@InjectYukiHookWithXposed
class WppXposed : IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog { tag = "WAE" }
            isEnableModuleAppResourcesCache = true
            isEnableDataChannel = true
        }
    }

    @SuppressLint("WorldReadableFiles")
    override fun onHook() = encase {

        loadSystem(Patch)
        loadSystem(ScopeHook)
        loadApp(hooker = AntiUpdater)

        loadApp(BuildConfig.APPLICATION_ID) {
            "android.app.ContextImpl".toClass().resolve().apply {
                firstMethod {
                    name = "getSharedPreferences"
                    parameters(String::class, Int::class)
                }.hook {
                    before {
                        if (args[1] == ContextWrapper.MODE_PRIVATE) {
                            @Suppress("DEPRECATION")
                            args[1] = ContextWrapper.MODE_WORLD_READABLE
                        }
                    }
                }
                firstMethod {
                    name = "checkMode"
                }.hook().intercept()
            }
        }

        loadApp(FeatureLoader.PACKAGE_WPP, FeatureLoader.PACKAGE_BUSINESS) {
            if (packageName == FeatureLoader.PACKAGE_WPP && !App.isOriginalPackage) return@loadApp

            withProcess(mainProcessName) {
                disableSecureFlag(this)
                loadHooker(GlobalResourceHooker())

                Instrumentation::class.resolve().method {
                    name = "callApplicationOnCreate"
                    parameters(Application::class)
                }.hookAll {
                    before {
                        val application = args[0] as Application
                        val pref =
                            YukiSharedPreference(prefs("${BuildConfig.APPLICATION_ID}_preferences"))
                        application.injectModuleAppResources()
                        loadHooker(FeatureLoader)
                        FeatureLoader.start(
                            appClassLoader!!,
                            application,
                            appInfo.sourceDir!!,
                            pref
                        )
                    }
                }
            }
        }
    }

    private fun disableSecureFlag(param: PackageParam) {
        param.apply {
            Window::class.resolve().apply {
                firstMethod {
                    name = "setFlags"
                    parameters(Int::class, Int::class)
                }.hook {
                    before {
                        args[0] = (args[0] as Int) and WindowManager.LayoutParams.FLAG_SECURE.inv()
                        args[1] = (args[1] as Int) and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    }
                }

                firstMethod {
                    name = "addFlags"
                    parameters(Int::class)
                }.hook {
                    before {
                        val newFlags =
                            (args[0] as Int) and WindowManager.LayoutParams.FLAG_SECURE.inv()
                        args[0] = newFlags
                        if (newFlags == 0) result = null
                    }
                }
            }
            YLog.debug("Secure flag disabled successfully")
        }
    }
}
