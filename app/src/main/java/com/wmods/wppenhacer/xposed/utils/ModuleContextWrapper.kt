package com.wmods.wppenhacer.xposed.utils

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.ContextThemeWrapper
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.FeatureLoader


class ModuleContextWrapper(base: Context) : ContextThemeWrapper(base, R.style.AppTheme) {

    override fun getApplicationContext(): Context {
        return FeatureLoader.moduleContext.applicationContext
    }

    override fun getClassLoader(): ClassLoader {
        return ModuleContextWrapper::class.java.classLoader
            ?: super.getClassLoader()
    }

    override fun getResources(): Resources {
        return FeatureLoader.moduleContext.resources
    }

    override fun getAssets(): AssetManager {
        return FeatureLoader.moduleContext.assets
    }
}
