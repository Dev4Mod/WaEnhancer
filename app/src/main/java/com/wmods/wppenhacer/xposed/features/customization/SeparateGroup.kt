package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MenuItem
import android.widget.BaseAdapter
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.regex.Pattern

class SeparateGroup(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        const val CHATS = 200
        const val STATUS = 300
        const val GROUPS = 500

        @JvmField
        var tabs = ArrayList<Int>()
        var tabInstances = HashMap<Int, Any>()
    }

    override fun doHook() {
        if (!prefs.getBoolean("separategroups", false)) return

        val bottomNavigationViewCls = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".BottomNavigationView"
        )
        XposedHelpers.findAndHookMethod(
            bottomNavigationViewCls,
            "getMaxItemCount",
            XC_MethodReplacement.returnConstant(6)
        )

        // Modifying tab list order
        hookTabList()

        // Setting group icon
        hookTabIcon()

        // Setting up fragments
        hookTabInstance()

        // Setting group tab name
        hookTabName()

        // Setting tab count
        hookTabCount()
    }

    override fun getPluginName(): String {
        return "Separate Group"
    }

    private fun hookTabCount() {
        val runMethod = Unobfuscator.loadTabCountMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(runMethod))

        val enableCountMethod = Unobfuscator.loadEnableCountTabMethod(classLoader)
        val badgeWrapperConstructor = Unobfuscator.loadEnableCountTabBadgeWrapper(classLoader)
        val badgeItemConstructor = Unobfuscator.loadEnableCountTabBadgeItem(classLoader)
        val emptyBadgeClass = Unobfuscator.loadEnableCountTabEmptyBadgeClass(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(enableCountMethod))

        XposedBridge.hookMethod(enableCountMethod, object : XC_MethodHook() {
            @SuppressLint("Range", "Recycle")
            override fun beforeHookedMethod(param: MethodHookParam) {
                val indexTab = param.args[2] as Int
                if (indexTab == tabs.indexOf(CHATS)) {
                    param.result = null

                    CoroutineScope(Dispatchers.IO).launch {
                        var chatCount = 0
                        var groupCount = 0
                        val db = MessageStore.getInstance().getDatabase()
                        val sql = "SELECT * FROM chat WHERE unseen_message_count != 0"
                        db?.rawQuery(sql, null)?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val jid = cursor.getInt(cursor.getColumnIndex("jid_row_id"))
                                val groupType = cursor.getInt(cursor.getColumnIndex("group_type"))
                                val archived = cursor.getInt(cursor.getColumnIndex("archived"))
                                val chatLocked = cursor.getInt(cursor.getColumnIndex("chat_lock"))
                                if (archived != 0 || (groupType != 0 && groupType != 6) || chatLocked != 0) {
                                    continue
                                }
                                val sql2 = "SELECT * FROM jid WHERE _id == ?"
                                db.rawQuery(sql2, arrayOf(jid.toString())).use { cursor1 ->
                                    if (!cursor1.moveToFirst()) continue
                                    val server = cursor1.getString(cursor1.getColumnIndex("server"))
                                    if (server == "g.us") {
                                        groupCount++
                                    } else {
                                        chatCount++
                                    }
                                }
                            }

                        }
                        if (tabs.contains(CHATS) && tabInstances.containsKey(CHATS)) {
                            param.args[1] = if (chatCount <= 0) {
                                XposedHelpers.getStaticObjectField(emptyBadgeClass, "A00")
                            } else {
                                badgeWrapperConstructor.newInstance(
                                    badgeItemConstructor.newInstance(
                                        chatCount
                                    )
                                )
                            }
                        }
                        if (tabs.contains(GROUPS) && tabInstances.containsKey(GROUPS)) {
                            val chatsBadge = if (groupCount <= 0) {
                                XposedHelpers.getStaticObjectField(emptyBadgeClass, "A00")
                            } else {
                                badgeWrapperConstructor.newInstance(
                                    badgeItemConstructor.newInstance(
                                        groupCount
                                    )
                                )
                            }
                            withContext(Dispatchers.Main) {
                                XposedBridge.invokeOriginalMethod(
                                    param.method,
                                    param.thisObject,
                                    arrayOf(param.args[0], chatsBadge, tabs.indexOf(GROUPS))
                                )
                            }
                        }
                    }
                }
            }
        })
    }

    private fun hookTabIcon() {
        val iconTabMethod = Unobfuscator.loadIconTabMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(iconTabMethod))
        val menuAddAndroidX = Unobfuscator.loadAddMenuAndroidX(classLoader)
        logDebug(menuAddAndroidX.toString())

        XposedBridge.hookMethod(iconTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val hooked = XposedBridge.hookMethod(menuAddAndroidX, object : XC_MethodHook() {
                    override fun afterHookedMethod(innerParam: MethodHookParam) {
                        if (innerParam.args.size > 2 && (innerParam.args[1] as Int) == GROUPS) {
                            val menuItem = innerParam.result as MenuItem
                            menuItem.setIcon(
                                Utils.getID(
                                    "home_tab_communities_selector",
                                    "drawable"
                                )
                            )
                        }
                    }
                })
                param.setObjectExtra("hooked", hooked)
            }

            @SuppressLint("ResourceType")
            override fun afterHookedMethod(param: MethodHookParam) {
                val hooked = param.getObjectExtra("hooked") as? Unhook
                hooked?.unhook()
            }
        })
    }

    @SuppressLint("ResourceType")
    private fun hookTabName() {
        val tabNameMethod = Unobfuscator.loadTabNameMethod(classLoader)
        XposedBridge.hookMethod(tabNameMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tab = param.args[0] as Int
                if (tab == GROUPS) {
                    param.result = UnobfuscatorCache.getInstance().getString("groups")
                }
            }
        })
    }

    private fun hookTabInstance() {
        val cFragClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            ".ConversationsFragment"
        )

        val getTabMethod = Unobfuscator.loadGetTabMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(getTabMethod))

        val methodTabInstance = Unobfuscator.loadTabFragmentMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(methodTabInstance))

        val recreateFragmentMethod = Unobfuscator.loadRecreateFragmentConstructor(classLoader)

        val pattern = Pattern.compile("android:switcher:\\d+:(\\d+)")

        val fragmentClass = Unobfuscator.loadFragmentClass(classLoader)

        XposedBridge.hookMethod(recreateFragmentMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val string: String
                val arg0 = param.args[0]
                if (arg0 is Bundle) {
                    @Suppress("DEPRECATION")
                    val state = arg0.getParcelable<android.os.Parcelable>("state") ?: return
                    string = state.toString()
                } else {
                    string = param.args[2].toString()
                }
                val matcher = pattern.matcher(string)
                if (matcher.find()) {
                    val tabId = matcher.group(1)?.toIntOrNull() ?: return
                    if (tabId == GROUPS || tabId == CHATS) {
                        val fragmentField = ReflectionUtils.getFieldByType(
                            param.thisObject.javaClass,
                            fragmentClass
                        )
                        val convFragment =
                            ReflectionUtils.getObjectField(fragmentField, param.thisObject)
                        tabInstances.remove(tabId)
                        if (convFragment != null) {
                            tabInstances[tabId] = convFragment
                        }
                    }
                }
            }
        })

        XposedBridge.hookMethod(getTabMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tabId = tabs[param.args[0] as Int]
                if (tabId == GROUPS || tabId == CHATS) {
                    val convFragment = cFragClass.declaredConstructors.first {
                        it.parameterCount == 0
                    }.newInstance()
                    param.result = convFragment
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val tabId = tabs[param.args[0] as Int]
                tabInstances.remove(tabId)
                param.result?.let { tabInstances[tabId] = it }
            }
        })

        XposedBridge.hookMethod(methodTabInstance, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val chatsList = param.result as List<*>
                val resultList = filterChat(param.thisObject, chatsList)
                param.result = resultList
            }
        })

        val fabintMethod = Unobfuscator.loadFabMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(fabintMethod))

        XposedBridge.hookMethod(fabintMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (tabInstances[GROUPS] == param.thisObject) {
                    param.result = GROUPS
                }
            }
        })

        val publishResultsMethod = Unobfuscator.loadGetFiltersMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(publishResultsMethod))

        XposedBridge.hookMethod(publishResultsMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val filters = param.args[1]
                val chatsList = XposedHelpers.getObjectField(filters, "values") as List<*>
                val baseField = ReflectionUtils.getFieldByExtendType(
                    publishResultsMethod.declaringClass,
                    BaseAdapter::class.java
                ) ?: return
                val convField = ReflectionUtils.getFieldByType(baseField.type, cFragClass)
                val thiz = convField.get(baseField.get(param.thisObject)) ?: return
                val resultList = filterChat(thiz, chatsList)
                XposedHelpers.setObjectField(filters, "values", resultList)
                XposedHelpers.setIntField(filters, "count", resultList.size)
            }
        })
    }

    private fun filterChat(thiz: Any, chatsList: List<*>): List<*> {
        val tabChat = tabInstances[CHATS]
        val tabGroup = tabInstances[GROUPS]

        if (tabChat != thiz && tabGroup != thiz) {
            return chatsList
        }

        val editableChatList = ArrayListFilter(tabGroup == thiz)
        @Suppress("UNCHECKED_CAST")
        editableChatList.addAll(chatsList as Collection<Any>)
        return editableChatList
    }

    private fun hookTabList() {
        val onCreateTabList = Unobfuscator.loadTabListMethod(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onCreateTabList))

        XposedBridge.hookMethod(onCreateTabList, object : XC_MethodHook() {
            @Suppress("UNCHECKED_CAST")
            override fun afterHookedMethod(param: MethodHookParam) {
                val resultTabs = param.result as? ArrayList<Int> ?: return
                tabs = resultTabs
                if (!tabs.contains(GROUPS)) {
                    tabs.add(if (tabs.isEmpty()) 0 else 1, GROUPS)
                }
            }
        })
    }

    class ArrayListFilter(private val isGroup: Boolean) : ArrayList<Any>() {

        override fun add(index: Int, element: Any) {
            if (checkGroup(element)) {
                super.add(index, element)
            }
        }

        override fun add(element: Any): Boolean {
            if (checkGroup(element)) {
                return super.add(element)
            }
            return true
        }

        override fun addAll(elements: Collection<Any>): Boolean {
            for (chat in elements) {
                if (checkGroup(chat)) {
                    super.add(chat)
                }
            }
            return true
        }

        private fun checkGroup(chat: Any?): Boolean {
            if (chat == null) return true

            var jid = getObjectField(chat, "A00")
            if (jid == null) jid = getObjectField(chat, "A01")
            if (jid == null) return true

            if (XposedHelpers.findMethodExactIfExists(jid.javaClass, "getServer") != null) {
                val server = callMethod(jid, "getServer") as? String ?: return true
                return if (isGroup) {
                    server == "broadcast" || server == "g.us"
                } else {
                    server == "s.whatsapp.net" || server == "lid"
                }
            }
            return true
        }
    }
}