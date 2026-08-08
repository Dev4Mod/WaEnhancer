package com.wmods.wppenhacer.xposed.features.listeners

import android.content.SharedPreferences
import android.view.View
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadAbsViewHolder
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadOnChangeStatus
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewHolderField1
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.CopyOnWriteArraySet

class ContactItemListener(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val onChangeStatus = loadOnChangeStatus(classLoader)
        val field1 = loadViewHolderField1(classLoader)
        val absViewHolderClass = loadAbsViewHolder(classLoader)
        val viewField = ReflectionUtils.findFieldUsingFilter(absViewHolderClass) { field ->
            field.type == View::class.java
        }

        XposedBridge.hookMethod(onChangeStatus, object : XC_MethodHook() {

            override fun afterHookedMethod(param: MethodHookParam) {
                if (contactListeners.isEmpty()) return
                val viewHolder = field1.get(param.thisObject) ?: return
                val `object` = param.args[0] ?: return
                val waContact = WaContactWpp(`object`)
                val userJid = waContact.userJid
                if (userJid.isNull) return

                val view = viewField.get(viewHolder) as? View ?: return

                for (listener in contactListeners) {
                    listener.onBind(waContact, view)
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Contact Item Listener"
    }

    abstract class OnContactItemListener {
        /**
         * Called when a contact item is bound in the RecyclerView
         *
         * @param waContact The user contact
         * @param view    The view associated with the item
         */
        abstract fun onBind(waContact: WaContactWpp?, view: View?)
    }

    companion object {
        @JvmField
        var contactListeners: CopyOnWriteArraySet<OnContactItemListener> =
            CopyOnWriteArraySet()
    }
}
