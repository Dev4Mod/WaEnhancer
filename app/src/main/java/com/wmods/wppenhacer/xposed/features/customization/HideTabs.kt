package com.wmods.wppenhacer.xposed.features.customization

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class HideTabs(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    private var mTabPagerInstance: Any? = null

    @Throws(Throwable::class)
    override fun doHook() {
        val hidetabs = prefs.getStringSet("hidetabs", null)
        val igstatus = prefs.getBoolean("igstatus", false)
        if (hidetabs.isNullOrEmpty()) return

        val hideTabsList = hidetabs.map { it.toInt() }

        val onCreateTabList = Unobfuscator.loadTabListMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList))

        XposedBridge.hookMethod(onCreateTabList, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                @Suppress("UNCHECKED_CAST")
            val tabs = param.result as ArrayList<Int>
                for (item in hideTabsList) {
                    if (item != SeparateGroup.STATUS || !igstatus) {
                        tabs.remove(item)
                    }
                }
            }
        })

        val onTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader)
        XposedBridge.hookMethod(onTabItemAddMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val menuItem = param.result as MenuItem
                val menuItemId = menuItem.itemId
                if (hideTabsList.contains(menuItemId)) {
                    menuItem.isVisible = false
                }
            }
        })

        val loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader)
        logDebug(loadTabFrameClass)

        XposedBridge.hookAllMethods(FrameLayout::class.java, "onMeasure", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!loadTabFrameClass.isInstance(param.thisObject)) return
                if (SeparateGroup.tabs.isNotEmpty()) {
                    val arr = ArrayList(SeparateGroup.tabs)
                    arr.removeAll(hideTabsList.toSet())
                    if (arr.size == 1) {
                        (param.thisObject as View).visibility = View.GONE
                    }
                }
                for (item in hideTabsList) {
                    val view = (param.thisObject as View).findViewById<View>(item)
                    if (view != null) {
                        view.visibility = View.GONE
                    }
                }
            }
        })

        XposedHelpers.findAndHookMethod(WppCore.homeActivityClass, "onCreate", Bundle::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val tabsPagerClass = WppCore.tabsPagerClass
                val tabsField = ReflectionUtils.getFieldByType(param.thisObject.javaClass, tabsPagerClass)
                mTabPagerInstance = tabsField!!.get(param.thisObject)
            }
        })

        val onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader)

        XposedBridge.hookMethod(onMenuItemSelected, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.thisObject == mTabPagerInstance) {
                    val index = param.args[0] as Int
                    val idxAtual = XposedHelpers.callMethod(param.thisObject, "getCurrentItem") as Int
                    param.args[0] = getNewTabIndex(hideTabsList, idxAtual, index)
                }
            }
        })

        XposedHelpers.findAndHookMethod(
            "androidx.viewpager.widget.ViewPager", classLoader, "addView",
            classLoader.loadClass("android.view.View"),
            Int::class.javaPrimitiveType,
            classLoader.loadClass($$"android.view.ViewGroup$LayoutParams"),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.thisObject != mTabPagerInstance) return
                    for (item in hideTabsList) {
                        val index = SeparateGroup.tabs.indexOf(item)
                        if (index == -1) continue
                        if (param.args[1] as Int == index) {
                            (param.args[0] as View).visibility = View.GONE
                        }
                    }
                }
            })
    }

    override fun getPluginName(): String {
        return "Hide Tabs"
    }

    private fun getNewTabIndex(hidetabs: List<Int>, indexAtual: Int, index: Int): Int {
        if (SeparateGroup.tabs.size <= index) return index
        val tabIsHidden = hidetabs.contains(SeparateGroup.tabs[index])
        if (!tabIsHidden) return index
        val newIndex = if (index > indexAtual) index + 1 else index - 1
        if (newIndex < 0) return 0
        if (newIndex >= SeparateGroup.tabs.size) return indexAtual
        return getNewTabIndex(hidetabs, indexAtual, newIndex)
    }
}
