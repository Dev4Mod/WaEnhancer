package com.wmods.wppenhacer.xposed

import android.content.pm.PackageInstaller
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import java.io.IOException

object AntiUpdater : YukiBaseHooker() {
    override fun onHook() {
        if (packageName == "android" || packageName == "system") return
        PackageInstaller::class.resolve().method {
            name = "createSession"
        }.hookAll {
            before {
                val session = args[0] as? PackageInstaller.SessionParams ?: return@before
                val target = session.javaClass.getDeclaredField("mPackageName")
                    .apply { isAccessible = true }.get(session)
                if (target == FeatureLoader.PACKAGE_WPP || target == FeatureLoader.PACKAGE_BUSINESS) {
                    IOException("UPDATE LOCKED BY WAENHANCER").throwToHost()
                }
            }
        }
    }
}
