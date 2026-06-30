package com.wmods.wppenhacer.xposed.features.listeners

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HeaderViewListAdapter
import android.widget.ListAdapter
import android.widget.ListView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.WeakHashMap

class ConversationItemListener(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {

    data class BoundConversationItem(
        val messageId: String,
        val rowId: Long,
        val message: FMessageWpp
    )

    companion object {
        private const val FIELD_BOUND_MESSAGE_ID = "conversation_item_bound_message_id"

        @JvmField
        val conversationListeners = HashSet<OnConversationItemListener>()

        var adapter: ListAdapter? = null

        @JvmField
        val listItems = WeakHashMap<View, BoundConversationItem>()

        private var hooked: XC_MethodHook.Unhook? = null

        @JvmStatic
        fun notifyDataSetChanged() {
            Handler(Looper.getMainLooper()).post {
                (adapter as? BaseAdapter)?.notifyDataSetChanged()
            }
        }

        fun getBoundMessageId(view: View): String? {
            return XposedHelpers.getAdditionalInstanceField(view, FIELD_BOUND_MESSAGE_ID) as? String
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
            XposedHelpers.setAdditionalInstanceField(view, FIELD_BOUND_MESSAGE_ID, boundItem.messageId)
            listItems[view] = boundItem
            return boundItem
        }
    }

    @Throws(Throwable::class)
    override fun doHook() {
        WppCore.addListenerActivity { activity, type ->
            if (activity.javaClass.simpleName == "Conversation" && type == WppCore.ActivityChangeState.ChangeType.DESTROYED)
                hooked?.unhook()
        }

        XposedHelpers.findAndHookMethod(
            ListView::class.java,
            "setAdapter",
            ListAdapter::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val currentActivity = WppCore.getCurrentActivity()
                    if (currentActivity == null || currentActivity.javaClass.simpleName != "Conversation") {
                        return
                    }

                    val listView = param.thisObject as ListView
                    if (listView.id != android.R.id.list) {
                        return
                    }

                    var currentAdapter = param.args[0] as? ListAdapter
                    if (currentAdapter is HeaderViewListAdapter) {
                        currentAdapter = currentAdapter.wrappedAdapter
                    }

                    if (currentAdapter == null) {
                        return
                    }

                    adapter = currentAdapter

                    for (listener in conversationListeners) {
                        listener.onAttachAdapter(adapter)
                    }

                    hooked?.unhook()

                    val method = adapter!!.javaClass.getDeclaredMethod(
                        "getView",
                        Int::class.javaPrimitiveType,
                        View::class.java,
                        ViewGroup::class.java
                    )

                    hooked = XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.thisObject !== adapter) return

                            val position = param.args[0] as Int
                            val convertView = param.args[1] as? View
                            val viewGroup = param.result as? ViewGroup ?: return

                            val fMessageObj = adapter!!.getItem(position) ?: return

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
                    })
                }
            }
        )
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
