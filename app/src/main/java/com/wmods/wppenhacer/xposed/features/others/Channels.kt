package com.wmods.wppenhacer.xposed.features.others

import android.view.Menu
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge

class Channels(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    private fun removeItems(
        arrList: MutableList<Any?>,
        channels: Boolean,
        removechannelRec: Boolean,
        headerChannelItem: Class<*>,
        listChannelItem: Class<*>,
        removeChannelRecClass: Class<*>
    ) {
        arrList.removeAll { e ->
            when {
                e == null -> false
                channels && (headerChannelItem.isInstance(e) || listChannelItem.isInstance(e)) -> true
                channels || removechannelRec -> removeChannelRecClass.isInstance(e)
                else -> false
            }
        }
    }

    override fun doHook() {
        val channels = prefs.getBoolean("channels", false)
        val removechannelRec = prefs.getBoolean("removechannel_rec", false)

        if (!channels && !removechannelRec) return

        val removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(classLoader)
        val headerChannelItem = Unobfuscator.loadHeaderChannelItemClass(classLoader)
        val listChannelItem = Unobfuscator.loadListChannelItemClass(classLoader)
        val listUpdateItems = Unobfuscator.loadListUpdateItems(classLoader)

        XposedBridge.hookMethod(listUpdateItems, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.setObjectExtra("isArgs", false)
                val listArgs =
                    ReflectionUtils.findInstancesOfType(param.args, List::class.javaObjectType)
                if (listArgs.isEmpty()) return
                val list = listArgs.first().second
                val index = listArgs.first().first
                val arrList = ArrayList(list)

                removeItems(
                    arrList,
                    channels,
                    removechannelRec,
                    headerChannelItem,
                    listChannelItem,
                    removeChannelRecClass
                )
                param.args[index] = arrList
                param.setObjectExtra("isArgs", true)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val isArg = param.getObjectExtra("isArgs") as Boolean? ?: false
                if (!isArg) {
                    val list = param.result as? java.util.ArrayList<*> ?: return
                    val arrList = ArrayList(list)
                    removeItems(
                        arrList,
                        channels,
                        removechannelRec,
                        headerChannelItem,
                        listChannelItem,
                        removeChannelRecClass
                    )
                    param.result = arrList
                }
            }
        })

        XposedBridge.hookAllConstructors(removeChannelRecClass, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val pairs =
                ReflectionUtils.findInstancesOfType(param.args, List::class.javaObjectType)
                for (pair in pairs) {
                    val index = pair.first as Int
                    param.args[index] = ArrayList<Any>()
                }
            }
        })

        if (channels) {
            XposedBridge.hookAllMethods(WppCore.homeActivityClass,"onPrepareOptionsMenu", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val menu = param.args[0] as? Menu ?: return
                    val id = Utils.getID("menuitem_create_newsletter", "id")
                    menu.findItem(id)?.isVisible = false
                }
            })
        }
    }

    override fun getPluginName(): String {
        return "Channels"
    }
}