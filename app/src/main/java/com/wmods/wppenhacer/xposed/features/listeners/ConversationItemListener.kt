package com.wmods.wppenhacer.xposed.features.listeners

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HeaderViewListAdapter
import android.widget.ListAdapter
import android.widget.ListView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArraySet

class ConversationItemListener(
    loader: ClassLoader,
    preferences: SharedPreferences
) : Feature(loader, preferences) {

    data class BoundConversationItem(
        val messageId: String,
        val rowId: Long,
        val message: FMessageWpp
    )

    companion object {
        private const val FIELD_BOUND_MESSAGE_ID = "conversation_item_bound_message_id"

        @JvmField
        val conversationListeners = CopyOnWriteArraySet<OnConversationItemListener>()

        var adapter: ListAdapter? = null

        @JvmField
        val listItems = WeakHashMap<View, BoundConversationItem>()

        private var hooked: YukiMemberHookCreator.MemberHookCreator.Result? = null
        private var adapterActivity: WeakReference<Activity>? = null

        @JvmStatic
        fun unwrapBaseAdapter(adapter: ListAdapter?): BaseAdapter? {
            var cur: Any? = adapter ?: return null
            if (cur is HeaderViewListAdapter) {
                cur = cur.wrappedAdapter
            }
            if (cur is BaseAdapter) {
                return cur
            }
            return cur?.javaClass?.declaredFields?.firstNotNullOfOrNull { field ->
                if (BaseAdapter::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    field.get(cur) as? BaseAdapter
                } else null
            }
        }

        @JvmStatic
        fun notifyDataSetChanged() {
            Handler(Looper.getMainLooper()).post {
                val baseAdapter = unwrapBaseAdapter(adapter)
                baseAdapter?.notifyDataSetChanged()
            }
        }

        fun getBoundMessageId(view: View): String? {
            return ReflectionUtils.getAdditionalInstanceField(
                view,
                FIELD_BOUND_MESSAGE_ID
            ) as? String
        }

        fun isViewBoundToMessage(view: View, messageId: String): Boolean {
            return getBoundMessageId(view) == messageId
        }

        private fun bindViewToMessage(view: View, fMessage: FMessageWpp): BoundConversationItem {
            val boundItem = BoundConversationItem(
                messageId = fMessage.key.messageID,
                rowId = fMessage.rowId,
                message = fMessage
            )
            ReflectionUtils.setAdditionalInstanceField(
                view,
                FIELD_BOUND_MESSAGE_ID,
                boundItem.messageId
            )
            listItems[view] = boundItem
            return boundItem
        }
    }

    @Throws(Throwable::class)
    override fun doHook() {
        WppCore.addListenerActivity { activity, type ->
            if (adapterActivity?.get() === activity && type == WppCore.ActivityChangeState.ChangeType.DESTROYED) {
                hooked?.remove()
                hooked = null
                adapter = null
                adapterActivity = null
                listItems.clear()
            }
        }

        ListView::class.java.resolve().firstMethod {
            name = "setAdapter"
            superclass()
            parameters(ListAdapter::class.java)
        }.hook {
            before {
                if (conversationListeners.isEmpty()) return@before
                val currentActivity = WppCore.getCurrentConversation()
                if (currentActivity == null) {
                    return@before
                }

                val listView = instance as ListView
                if (listView.id != android.R.id.list) {
                    return@before
                }

                var currentAdapter = args[0] as? ListAdapter
                if (currentAdapter is HeaderViewListAdapter) {
                    currentAdapter = currentAdapter.wrappedAdapter
                }

                if (currentAdapter == null) {
                    return@before
                }

                adapter = currentAdapter
                adapterActivity = WeakReference(currentActivity)

                for (listener in conversationListeners) {
                    listener.onAttachAdapter(adapter)
                }

                hooked?.remove()

                val method = ReflectionUtils.findMethodBestMatch(
                    adapter!!.javaClass, "getView",
                    Int::class.javaPrimitiveType,
                    View::class.java,
                    ViewGroup::class.java
                )

                hooked = method.hook {
                    after {
                        if (conversationListeners.isEmpty()) return@after
                        val activeAdapter = adapter ?: return@after
                        if (instanceOrNull !== activeAdapter) return@after

                        val position = args[0] as Int
                        val convertView = args[1] as? View
                        val viewGroup = result as? ViewGroup ?: return@after

                        val fMessageObj = activeAdapter.getItem(position) ?: return@after

                        if (!FMessageWpp.TYPE.isInstance(fMessageObj)) return@after
                        val fMessage = FMessageWpp(fMessageObj)

                        bindViewToMessage(viewGroup, fMessage)

                        for (listener in conversationListeners) {
                            try {
                                listener.onItemBind(fMessage, viewGroup, position, convertView)
                            } catch (e: Throwable) {
                                logDebug(e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Conversation Item Listener"
    }

    abstract class OnConversationItemListener {
        /**
         * Called when a message item is rendered in the conversation
         *
         * @param fMessage The message
         * @param view     The view associated with the item
         * @param position The position
         * @param convertView The view from the adapter
         * @throws Throwable Errors caught in the hook
         */
        @Throws(Throwable::class)
        abstract fun onItemBind(
            fMessage: FMessageWpp,
            view: ViewGroup,
            position: Int,
            convertView: View?
        )

        open fun onAttachAdapter(adapter: ListAdapter?) {
            // TODO
        }
    }
}
