package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.ListView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.adapter.IGStatusAdapter
import com.wmods.wppenhacer.views.IGStatusView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

private val mListStatusContainer = ArrayList<IGStatusView>()

class IGStatus(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {

    companion object {
        @JvmField
        var itens = ArrayList<Any?>()

        private const val FORCE_LOAD_DELAY_MS = 1500L
        private const val PAGER_RETRY_MS = 500L
        private const val PAGER_MAX_ATTEMPTS = 30
        private const val PAGER_OFFSCREEN_PAGES = 6
        private const val PAGER_RESTORE_DELAY_MS = 2000L
    }

    private var raisedPager: WeakReference<ViewGroup>? = null
    private var originalPageLimit = 1

    // UpdatesFragment instances whose async init already ran, naturally or forced
    private val initializedFragments = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )

    @Throws(Throwable::class)
    override fun doHook() {
        if (!xprefs.getBoolean("igstatus", false)) return

        forceStatusLoad()

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
                    val activity = WppCore.getCurrentActivity()!!
                    if (mStatusContainer.adapter?.context !== activity) {
                        mStatusContainer.adapter = IGStatusAdapter(activity, statusInfoClass)
                    }
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

    private fun forceStatusLoad() {
        val onStart = runCatching { Unobfuscator.loadUpdatesFragmentOnStartMethod(classLoader) }
            .onFailure { logDebug(it) }.getOrNull() ?: return
        val asyncInit =
            runCatching { Unobfuscator.loadUpdatesFragmentAsyncInitMethod(classLoader) }
                .onFailure { logDebug(it) }.getOrNull() ?: return

        asyncInit.hook {
            before { initializedFragments.add(instance) }
        }

        val handler = Handler(Looper.getMainLooper())
        keepAllTabsAlive(handler)
        onStart.hook {
            after {
                val fragment = instance
                logDebug("UpdatesFragment.onStart", fragment)
                handler.postDelayed({
                    if (fragment !in initializedFragments) {
                        runCatching {
                            initializedFragments.add(fragment)
                            asyncInit.invoke(fragment)
                        }.onFailure { logDebug(it) }
                    }
                    handler.postDelayed({ restorePageLimit() }, PAGER_RESTORE_DELAY_MS)
                }, FORCE_LOAD_DELAY_MS)
            }
        }
    }

    private fun keepAllTabsAlive(handler: Handler) {
        WppCore.homeActivityClass.resolve().method {
            name = "onCreate"
            parameters(Bundle::class)
        }.hookAll {
            after {
                val activity = instance<Activity>()
                var attempts = 0
                handler.post(object : Runnable {
                    override fun run() {
                        val pager = findTabsPager(activity.window.decorView)
                        val ready = pager != null &&
                            ReflectionUtils.callMethod(pager, "getAdapter") != null
                        if (pager == null || !ready) {
                            if (++attempts < PAGER_MAX_ATTEMPTS && !activity.isFinishing) {
                                handler.postDelayed(this, PAGER_RETRY_MS)
                            } else {
                                logDebug("home pager not found", "$attempts pager=${pager?.javaClass?.name}")
                            }
                            return
                        }
                        val limit = ReflectionUtils.callMethod(pager, "getOffscreenPageLimit") as? Int ?: 1
                        logDebug("home pager", "limit=$limit attempts=$attempts")
                        if (limit < PAGER_OFFSCREEN_PAGES) {
                            runCatching {
                                ReflectionUtils.callMethod(pager, "setOffscreenPageLimit", PAGER_OFFSCREEN_PAGES)
                                raisedPager = WeakReference(pager)
                                originalPageLimit = limit
                            }.onFailure { logDebug(it) }
                        }
                    }
                })
            }
        }
    }

    private fun restorePageLimit() {
        val pager = raisedPager?.get() ?: return
        raisedPager = null
        runCatching {
            ReflectionUtils.callMethod(pager, "setOffscreenPageLimit", originalPageLimit)
        }.onFailure { logDebug(it) }
        logDebug("home pager", "restored limit=$originalPageLimit")
    }

    private fun isViewPager(view: View): Boolean {
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            if (cls.name == "androidx.viewpager.widget.ViewPager") return true
            cls = cls.superclass
        }
        return false
    }

    private fun findTabsPager(view: View): ViewGroup? {
        if (isViewPager(view)) return view as ViewGroup
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findTabsPager(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    override fun getPluginName(): String {
        return "IGStatus"
    }
}
