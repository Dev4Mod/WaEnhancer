package com.wmods.wppenhacer.xposed.features.listeners

import android.content.SharedPreferences
import android.view.View
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadAbsViewHolder
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadOnChangeStatus
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewHolderField1
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
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

        onChangeStatus.hook {
            after {
                if (contactListeners.isEmpty()) return@after
                val viewHolder = field1.get(instanceOrNull) ?: return@after
                val `object` = args[0] ?: return@after
                val waContact = WaContactWpp(`object`)
                val userJid = waContact.userJid
                if (userJid.isNull) return@after

                val view = viewField.get(viewHolder) as? View ?: return@after

                for (listener in contactListeners) {
                    listener.onBind(waContact, view)
                }
            }
        }
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
