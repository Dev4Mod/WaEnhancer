package com.wmods.wppenhacer.xposed.features.others

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ModuleContextWrapper
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class CopySelectionMessage(classLoader: ClassLoader, prefs: SharedPreferences) :
    Feature(classLoader, prefs) {

    override fun doHook() {
        if (!prefs.getBoolean("copy_selection_message", false)) return

        val popupWindowMessage = Unobfuscator.loadPopupWindowMessageClass(classLoader)
        XposedBridge.hookAllConstructors(popupWindowMessage, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = WppCore.getCurrentActivity() ?: run {
                    logDebug("CurrentActivity is null")
                    return
                }
                val mainPopupWindow = param.thisObject as PopupWindow
                val viewGroup = mainPopupWindow.contentView as? ViewGroup ?: return

                val fMessageObj = param.args?.filterIsInstance(FMessageWpp.TYPE)?.firstOrNull()
                    ?: return
                val fMessage = FMessageWpp(fMessageObj)
                val messageText = fMessage.messageStr ?: ""

                if (messageText.isEmpty()) return

                val copyButton = buildActionPill(activity)

                copyButton.setOnClickListener {
                    try {
                        mainPopupWindow.dismiss()
                    } catch (_: Throwable) {
                    }
                    val view = ConversationItemListener.listItems.entries.firstOrNull {
                        it.value.messageId == fMessage.key.messageID &&
                                ConversationItemListener.isViewBoundToMessage(
                                    it.key,
                                    fMessage.key.messageID
                                )
                    }?.key
                    val textView = view?.findViewById<TextView>(Utils.getID("message_text", "id"))
                    showSelectionDialog(activity, textView?.text ?: messageText)
                }

                val layout = viewGroup.findViewById<LinearLayout>(
                    Utils.getID("reactions_tray_layout", "id")
                ) ?: return
                layout.orientation = LinearLayout.VERTICAL
                val parentItems = layout.children.toList()
                layout.removeAllViews()
                val newContainer = LinearLayout(viewGroup.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    parentItems.forEach { addView(it) }
                }
                layout.addView(newContainer)
                layout.addView(copyButton)
            }
        })
    }

    private fun buildActionPill(activity: Activity): MaterialButton {
        val ctx = ModuleContextWrapper(activity)
        val textColor = DesignUtils.getPrimaryTextColor()
        val strokeColor = Color.argb(
            80,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )
        return MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = Utils.getString(R.string.copy_selection_action)
            setTextColor(textColor)
            setStrokeColor(ColorStateList.valueOf(strokeColor))
            cornerRadius = Utils.dipToPixels(50f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSelectionDialog(activity: Activity, messageText: CharSequence) {
        val d = activity.resources.displayMetrics.density
        fun Int.dp() = (this * d).toInt()

        val ctx = ModuleContextWrapper(activity)

        val textInputLayout = TextInputLayout(
            ctx,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            isCounterEnabled = true
            hint = Utils.getString(R.string.message)
        }
        val editText = TextInputEditText(textInputLayout.context).apply {
            setText(messageText)
            textSize = 14f
            minLines = 3
            maxLines = 10
            gravity = Gravity.TOP
            setLineSpacing(0f, 1.4f)
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setSelection(0)
        }
        textInputLayout.addView(editText)

        val scrollView = NestedScrollView(ctx).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            addView(textInputLayout)
        }

        val textColor = DesignUtils.getPrimaryTextColor()
        val outlineColor = Color.argb(
            60,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )

        val closeButton = MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = Utils.getString(R.string.close)
            setTextColor(textColor)
            setStrokeColor(ColorStateList.valueOf(outlineColor))
            cornerRadius = Utils.dipToPixels(50f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = 12.dp()
            }
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 8.dp(), 16.dp(), 16.dp())
            addView(scrollView)
            addView(closeButton)
        }

        val dialog = AlertDialogWpp(activity)
            .setTitle(Utils.getString(R.string.copy_selection_dialog_title))
            .setView(container)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun getPluginName(): String = "Copy Selection Message"
}
