package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.view.Menu
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class Channels(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

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
        val channels = xprefs.getBoolean("channels", false)
        val removechannelRec = xprefs.getBoolean("removechannel_rec", false)

        if (!channels && !removechannelRec) return

        val removeChannelRecClass = Unobfuscator.loadRemoveChannelRecClass(classLoader)
        val headerChannelItem = Unobfuscator.loadHeaderChannelItemClass(classLoader)
        val listChannelItem = Unobfuscator.loadListChannelItemClass(classLoader)
        val listUpdateItems = Unobfuscator.loadListUpdateItems(classLoader)

        listUpdateItems.hook {
            before {
                dataExtra.putBoolean("isArgs", false)
                val listArgs =
                    ReflectionUtils.findInstancesOfType(args, List::class.javaObjectType)
                if (listArgs.isEmpty()) return@before
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
                args[index] = arrList
                dataExtra.putBoolean("isArgs", true)
            }
            after {
                val isArg = dataExtra.getBoolean("isArgs", false)
                if (!isArg) {
                    val list = result as? java.util.ArrayList<*> ?: return@after
                    val arrList = ArrayList(list)
                    removeItems(
                        arrList,
                        channels,
                        removechannelRec,
                        headerChannelItem,
                        listChannelItem,
                        removeChannelRecClass
                    )
                    result = arrList
                }
            }
        }

        removeChannelRecClass.resolve().constructor { }.hookAll {
            before {
                val pairs =
                    ReflectionUtils.findInstancesOfType(args, List::class.javaObjectType)
                for (pair in pairs) {
                    val index = pair.first as Int
                    args[index] = ArrayList<Any>()
                }
            }
        }

        if (channels) {
            WppCore.homeActivityClass.resolve().method {
                name = "onPrepareOptionsMenu"
            }.hookAll {
                after {
                    val menu = args[0] as? Menu ?: return@after
                    val id = Utils.getID("menuitem_create_newsletter", "id")
                    menu.findItem(id)?.isVisible = false
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Channels"
    }
}