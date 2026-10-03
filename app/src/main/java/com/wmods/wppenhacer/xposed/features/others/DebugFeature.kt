package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.wmods.wppenhacer.xposed.core.Feature

class DebugFeature(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
    }


    override fun getPluginName(): String {
        return "Debug Feature"
    }
}
