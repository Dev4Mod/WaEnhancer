package com.wmods.wppenhacer.xposed.utils

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.ContextThemeWrapper
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.FeatureLoader


class ModuleContextWrapper(private val base: Context) :
    ContextThemeWrapper(base, R.style.AppTheme) {

    private var customTheme: Resources.Theme? = null

    override fun getApplicationContext(): Context {
        return base.applicationContext ?: base
    }

    override fun getClassLoader(): ClassLoader {
        return ModuleContextWrapper::class.java.classLoader
            ?: super.getClassLoader()
    }

    override fun getResources(): Resources {
        return runCatching { FeatureLoader.moduleContext.resources }.getOrElse { base.resources }
    }

    override fun getAssets(): AssetManager {
        return runCatching { FeatureLoader.moduleContext.assets }.getOrElse { base.assets }
    }

    override fun getTheme(): Resources.Theme {
        if (customTheme == null) {
            try {
                val theme = resources.newTheme()
                theme.applyStyle(R.style.AppTheme, true)
                customTheme = theme
            } catch (_: Throwable) {
                return super.getTheme()
            }
        }
        return customTheme ?: super.getTheme()
    }
}
