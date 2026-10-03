package com.wmods.wppenhacer.xposed.features.customization

import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ListView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.adapter.IGStatusAdapter
import com.wmods.wppenhacer.views.IGStatusView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType

private val mListStatusContainer = ArrayList<IGStatusView>()

class IGStatus(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    companion object {
        @JvmField
        var itens = ArrayList<Any?>()
    }

    @Throws(Throwable::class)
    override fun doHook() {
        if (!xprefs.getBoolean("igstatus", false)) return

        val fabintMethod = Unobfuscator.loadFabMethod(classLoader)

        val archivedFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".ArchivedConversationsFragment"
        )
        val folderFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader, StringMatchType.EndsWith, ".FolderConversationsFragment"
        )

        val getViewConversationMethod = Unobfuscator.loadGetViewConversationMethod(classLoader)
        getViewConversationMethod.hook {
            after {
                if (archivedFragmentClass.isInstance(instance)) return@after
                if (folderFragmentClass.isInstance(instance)) return@after
                val view = result as? ViewGroup ?: return@after
                val list = view.findViewById<ViewGroup>(android.R.id.list)
                val mStatusContainer = IGStatusView(WppCore.getCurrentActivity()!!)
                if (list is ListView) {
                    list.isNestedScrollingEnabled = true
                    val layoutParams = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88)
                    )
                    mStatusContainer.layoutParams = layoutParams
                    list.addHeaderView(mStatusContainer)
                } else {
                    val paddingTop = list.paddingTop
                    val parentView = list.parent as ViewGroup
                    val background = list.background
                    mStatusContainer.background = background
                    list.setPadding(0, 0, 0, 0)
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Utils.dipToPixels(88)
                    )
                    layoutParams.topMargin = paddingTop
                    mStatusContainer.layoutParams = layoutParams
                    parentView.addView(mStatusContainer, 0)
                }
                val id = fabintMethod.invoke(instance) as Int
                val igStatus = mListStatusContainer.find { it.fragmentId == id }
                if (igStatus != null) {
                    mStatusContainer.adapter = igStatus.adapter
                    mListStatusContainer.remove(igStatus)
                }
                mStatusContainer.fragmentId = id
                mListStatusContainer.add(mStatusContainer)
            }
        }

        val onUpdateStatusChanged = Unobfuscator.loadOnUpdateStatusChanged(classLoader)
        logDebug(Unobfuscator.getMethodDescriptor(onUpdateStatusChanged))
        val statusInfoClass = Unobfuscator.loadStatusInfoClass(classLoader)
        logDebug(statusInfoClass)

        val updateModel = onUpdateStatusChanged.declaringClass
        logDebug(updateModel)

        updateModel.resolve().constructor { }.hookAll {
            after {
                val newList = ArrayList(itens)
                newList.add(0, null)
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    val mStatusAdapter =
                        IGStatusAdapter(WppCore.getCurrentActivity()!!, statusInfoClass)
                    mStatusContainer.adapter = mStatusAdapter
                    mStatusContainer.updateList()
                }
            }
        }

        val onStatusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)
        logDebug(onStatusListUpdatesClass)

        onStatusListUpdatesClass.resolve().constructor { }.hookAll {
            before {
                val lists = args.filterIsInstance<List<*>>()
                val newList = ArrayList<Any?>()
                newList.add(0, null)
                newList.addAll(lists[0])
                newList.addAll(lists[1])
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    mStatusContainer.updateList()
                }
            }
        }

        val onGetInvokeField = Unobfuscator.loadGetInvokeField(classLoader)
        logDebug(Unobfuscator.getFieldDescriptor(onGetInvokeField))
        onUpdateStatusChanged.hook {
            before {
                val obj = onGetInvokeField.get(args[0])
                val method = ReflectionUtils.findMethodUsingFilter(
                    obj.javaClass
                ) { m -> m.returnType == Any::class.java }
                val statusListUpdates = ReflectionUtils.callMethod(method, obj) ?: return@before
                val lists = ReflectionUtils.findAllFieldsUsingFilter(
                    statusListUpdates.javaClass
                ) { f -> f.type == List::class.java }
                if (lists.size < 3) return@before
                val list1 = lists[1].get(statusListUpdates) as List<*>
                val list2 = lists[2].get(statusListUpdates) as List<*>
                val newList = ArrayList<Any?>()
                newList.add(0, null)
                newList.addAll(list1)
                newList.addAll(list2)
                itens = newList
                for (mStatusContainer in mListStatusContainer) {
                    mStatusContainer.updateList()
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "IGStatus"
    }
}
