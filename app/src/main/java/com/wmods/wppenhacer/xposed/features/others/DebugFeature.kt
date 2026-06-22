package com.wmods.wppenhacer.xposed.features.others

import com.wmods.wppenhacer.xposed.core.Feature
import de.robv.android.xposed.XSharedPreferences

class DebugFeature(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
    }


    override fun getPluginName(): String {
        return "Debug Feature"
    }
}
