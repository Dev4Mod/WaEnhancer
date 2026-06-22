package com.wmods.wppenhacer.xposed.features.others

import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadFilterAdaperClass
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.function.Predicate

class ChatFilters(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {


    override fun doHook() {
        if (!prefs.getBoolean("separategroups", false)) return

        val filterAdaperClass = loadFilterAdaperClass(classLoader)
        XposedBridge.hookAllConstructors(filterAdaperClass, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val list = ReflectionUtils.findInstancesOfType(
                    param.args,
                    MutableList::class.java
                )
                if (!list.isEmpty()) {
                    val argResult = list.first()
                    val newList = ArrayList(argResult.second)
                    newList.removeIf { item: Any? ->
                        val name = XposedHelpers.getObjectField(item, "A01")
                        name == null || name === "CONTACTS_FILTER" || name === "GROUP_FILTER"
                    }
                    param.args[argResult.first!!] = newList
                }
            }
        })
        val methodSetFilter = ReflectionUtils.findMethodUsingFilter(filterAdaperClass) {
            method -> method.parameterCount == 1 && method.parameterTypes[0] == Int::class.javaPrimitiveType
        }

        XposedBridge.hookMethod(methodSetFilter, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val index = param.args[0] as Int
                val field = ReflectionUtils.getFieldByType(
                    methodSetFilter.declaringClass,
                    MutableList::class.java
                )
                val list = field!!.get(param.thisObject) as MutableList<*>?
                if (list == null || index >= list.size) {
                    param.setResult(null)
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Chat Filters"
    }
}
