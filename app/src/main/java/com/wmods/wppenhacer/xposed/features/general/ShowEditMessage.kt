package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.adapter.MessageAdapter
import com.wmods.wppenhacer.views.NoScrollListView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore.MessageItem
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadCallerMessageEditMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadGetEditMessageMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadMessageEditMethod
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener.OnConversationItemListener
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class ShowEditMessage(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("antieditmessages", false)) return

        val onMessageEdit = loadMessageEditMethod(classLoader)
        logDebug(getMethodDescriptor(onMessageEdit))

        val callerMessageEditMethod = loadCallerMessageEditMethod(classLoader)
        logDebug(getMethodDescriptor(callerMessageEditMethod))

        val getEditMessage = loadGetEditMessageMethod(classLoader)
        logDebug(getMethodDescriptor(getEditMessage))

        XposedBridge.hookMethod(onMessageEdit, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val invoked = callerMessageEditMethod.invoke(null, param.args[0])
                val timestamp = XposedHelpers.getLongField(invoked, "A00")
                val fMessage = FMessageWpp(param.args[0])
                val id = fMessage.rowId
                val origMessage = MessageStore.getInstance().getCurrentMessageByID(id)
                var newMessage = fMessage.messageStr
                if (newMessage == null) {
                    val methods = ReflectionUtils.findAllMethodsUsingFilter(
                        param.args[0].javaClass
                    ) { method ->
                        method.returnType == String::class.java && ReflectionUtils.isOverridden(
                            method
                        )
                    }
                    for (method in methods) {
                        newMessage = method!!.invoke(param.args[0]) as String?
                        if (newMessage != null) break
                    }
                    if (newMessage == null) return
                }
                try {
                    val message = MessageHistoryStore.getInstance().getMessages(id)
                    if (message == null) {
                        MessageHistoryStore.getInstance().insertMessage(id, origMessage, 0)
                    }
                    MessageHistoryStore.getInstance().insertMessage(id, newMessage, timestamp)
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        })

        val strEmoji = "\uD83D\uDCDD"

        ConversationItemListener.conversationListeners.add(
            object : OnConversationItemListener() {
                override fun onItemBind(
                    fMessage: FMessageWpp,
                    view: ViewGroup,
                    position: Int,
                    convertView: View?
                ) {
                    val textView =
                        view.findViewById<View?>(Utils.getID("edit_label", "id")) as TextView?
                    if (textView != null && !textView.text.toString().contains(strEmoji)) {
                        textView.paint.isUnderlineText = true
                        textView.append(strEmoji)
                        textView.setOnClickListener {
                            try {
                                val id = fMessage.rowId
                                var messages = MessageHistoryStore.getInstance().getMessages(id)
                                if (messages == null) {
                                    messages = ArrayList()
                                }
                                showBottomDialog(messages)
                            } catch (exception0: Exception) {
                                logDebug(exception0)
                            }
                        }
                    }
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun showBottomDialog(messages: ArrayList<MessageItem>) {
        WppCore.getCurrentActivity()?.runOnUiThread {
            val ctx = WppCore.getCurrentActivity()
            val dialog = WppCore.createBottomDialog(ctx!!)
            // NestedScrollView
            val nestedScrollView0 = NestedScrollView(ctx, null)
            nestedScrollView0.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            nestedScrollView0.isFillViewport = true
            nestedScrollView0.fitsSystemWindows = true
            // Main Layout
            val linearLayout = LinearLayout(ctx)
            linearLayout.orientation = LinearLayout.VERTICAL
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            linearLayout.fitsSystemWindows = true
            linearLayout.minimumHeight = (Utils.application.resources
                .displayMetrics.heightPixels / 4).also { layoutParams.height = it }
            linearLayout.layoutParams = layoutParams
            val dip = Utils.dipToPixels(20)
            linearLayout.setPadding(dip, dip, dip, 0)
            val bg =
                DesignUtils.createDrawable("rc_dialog_bg", DesignUtils.getPrimarySurfaceColor())
            linearLayout.background = bg

            // Title View
            val titleView = TextView(ctx)
            val layoutParams1 = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams1.weight = 1.0f
            layoutParams1.setMargins(0, 0, 0, Utils.dipToPixels(10))
            titleView.layoutParams = layoutParams1
            titleView.textSize = 16.0f
            titleView.setTextColor(DesignUtils.getPrimaryTextColor())
            titleView.setTypeface(null, Typeface.BOLD)
            titleView.setText(R.string.edited_history)

            // List View
            val adapter = MessageAdapter(ctx, messages)
            val listView: ListView = NoScrollListView(ctx)
            val layoutParams2 = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            layoutParams2.weight = 1.0f
            listView.layoutParams = layoutParams2
            listView.adapter = adapter
            val imageView0 = ImageView(ctx)
            val layoutParams4 =
                LinearLayout.LayoutParams(Utils.dipToPixels(70), Utils.dipToPixels(8))
            layoutParams4.gravity = 17
            layoutParams4.setMargins(0, Utils.dipToPixels(5), 0, Utils.dipToPixels(5))
            val bg2 = DesignUtils.createDrawable("rc_dotline_dialog", Color.BLACK)
            imageView0.background = DesignUtils.alphaDrawable(
                bg2,
                DesignUtils.getPrimaryTextColor(),
                33
            )
            imageView0.layoutParams = layoutParams4
            // Button View
            val okButton = Button(ctx)
            val layoutParams3 = LinearLayout.LayoutParams(-1, -2)
            layoutParams3.setMargins(0, Utils.dipToPixels(10), 0, Utils.dipToPixels(10))
            layoutParams3.gravity = 80
            okButton.layoutParams = layoutParams3
            okButton.gravity = 17
            val drawable = DesignUtils.createDrawable("selector_bg", Color.BLACK)
            okButton.background = DesignUtils.alphaDrawable(
                drawable,
                DesignUtils.getPrimaryTextColor(),
                25
            )
            okButton.text = "OK"
            okButton.setOnClickListener { dialog.dismissDialog() }
            linearLayout.addView(imageView0)
            linearLayout.addView(titleView)
            linearLayout.addView(listView)
            linearLayout.addView(okButton)
            nestedScrollView0.addView(linearLayout)
            dialog.setContentView(nestedScrollView0)
            dialog.setCanceledOnTouchOutside(true)
            dialog.showDialog()
        }
    }


    override fun getPluginName(): String {
        return "Show Edit Message"
    }
}
