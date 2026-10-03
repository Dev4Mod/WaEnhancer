package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils

class HideTabs(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    private var mTabPagerInstance: Any? = null

    @Throws(Throwable::class)
    override fun doHook() {
        val hidetabs = xprefs.getStringSet("hidetabs", null)
        val igstatus = xprefs.getBoolean("igstatus", false)
        if (hidetabs.isNullOrEmpty()) return

        val hideTabsList = hidetabs.map { it.toInt() }

        val onCreateTabList = Unobfuscator.loadTabListMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList))

        onCreateTabList.hook {
            after {
                @Suppress("UNCHECKED_CAST")
                val tabs = result as ArrayList<Int>
                for (item in hideTabsList) {
                    if (item != SeparateGroup.STATUS || !igstatus) {
                        tabs.remove(item)
                    }
                }
            }
        }

        val onTabItemAddMethod = Unobfuscator.loadOnTabItemAddMethod(classLoader)
        onTabItemAddMethod.hook {
            after {
                val menuItem = result as MenuItem
                val menuItemId = menuItem.itemId
                if (hideTabsList.contains(menuItemId)) {
                    menuItem.isVisible = false
                }
            }
        }

        val loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader)
        logDebug(loadTabFrameClass)

        FrameLayout::class.java.resolve().method {
            name = "onMeasure"
        }.hookAll {
            after {
                if (!loadTabFrameClass.isInstance(instance)) return@after
                if (SeparateGroup.tabs.isNotEmpty()) {
                    val arr = ArrayList(SeparateGroup.tabs)
                    arr.removeAll(hideTabsList.toSet())
                    if (arr.size == 1) {
                        (instance as View).visibility = View.GONE
                    }
                }
                for (item in hideTabsList) {
                    val view = (instance as View).findViewById<View>(item)
                    if (view != null) {
                        view.visibility = View.GONE
                    }
                }
            }
        }

        WppCore.homeActivityClass.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                val tabsPagerClass = WppCore.tabsPagerClass
                val tabsField = ReflectionUtils.getFieldByType(instance.javaClass, tabsPagerClass)
                mTabPagerInstance = tabsField!!.get(instance)
            }
        }

        val onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader)

        onMenuItemSelected.hook {
            before {
                if (instanceOrNull == mTabPagerInstance) {
                    val index = args[0] as Int
                    val idxAtual = ReflectionUtils.callMethod(instance, "getCurrentItem") as Int
                    args[0] = getNewTabIndex(hideTabsList, idxAtual, index)
                }
            }
        }

        ReflectionUtils.findClass("androidx.viewpager.widget.ViewPager", classLoader).resolve()
            .firstMethod {
                name = "addView"
                superclass()
                parameters(
                    classLoader.loadClass("android.view.View"),
                    Int::class,
                    classLoader.loadClass($$"android.view.ViewGroup$LayoutParams")
                )
            }.hook {
            before {
                if (instanceOrNull != mTabPagerInstance) return@before
                for (item in hideTabsList) {
                    val index = SeparateGroup.tabs.indexOf(item)
                    if (index == -1) continue
                    if (args[1] as Int == index) {
                        (args[0] as View).visibility = View.GONE
                    }
                }
            }
        }
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
