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
        // YukiHookAPI's loadSystem() only matches the "android" package.
        // ScopeHook also contains the SettingsProvider bridge fallback, so it
        // must be loaded explicitly in that process as well.
        loadApp("com.android.providers.settings", ScopeHook)
        loadApp(hooker = AntiUpdater)

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
                        application.injectModuleAppResources()
                        loadHooker(FeatureLoader)
                        FeatureLoader.start(
                            appClassLoader!!,
                            application,
                            appInfo.sourceDir!!)
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
