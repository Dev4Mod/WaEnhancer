package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadFilterAdaperClass
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils

class ChatFilters(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {


    override fun doHook() {
        if (!xprefs.getBoolean("separategroups", false)) return

        val filterAdaperClass = loadFilterAdaperClass(classLoader)
        filterAdaperClass.resolve().constructor { }.hookAll {
            before {
                val list = ReflectionUtils.findInstancesOfType(
                    args,
                    MutableList::class.java
                )
                if (!list.isEmpty()) {
                    val argResult = list.first()
                    val newList = ArrayList(argResult.second)
                    newList.removeIf { item: Any? ->
                        val name = ReflectionUtils.getObjectField(item, "A01")
                        name == null || name === "CONTACTS_FILTER" || name === "GROUP_FILTER"
                    }
                    args[argResult.first!!] = newList
                }
            }
        }
        val methodSetFilter = ReflectionUtils.findMethodUsingFilter(filterAdaperClass) { method ->
            method.parameterCount == 1 && method.parameterTypes[0] == Int::class.javaPrimitiveType
        }

        methodSetFilter.hook {
            before {
                val index = args[0] as Int
                val field = ReflectionUtils.getFieldByType(
                    methodSetFilter.declaringClass,
                    MutableList::class.java
                )
                val list = field!!.get(instance) as MutableList<*>?
                if (list == null || index >= list.size) {
                    result = (null)
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Chat Filters"
    }
}
