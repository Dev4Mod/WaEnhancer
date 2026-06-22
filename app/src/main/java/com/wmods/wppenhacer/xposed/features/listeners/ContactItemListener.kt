package com.wmods.wppenhacer.xposed.features.listeners

import android.view.View
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getFieldDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadAbsViewHolder
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadOnChangeStatus
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadViewHolderField1
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

class ContactItemListener(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val onChangeStatus = loadOnChangeStatus(classLoader)
        logDebug(getMethodDescriptor(onChangeStatus))
        val field1 = loadViewHolderField1(classLoader)
        logDebug(getFieldDescriptor(field1))
        val absViewHolderClass = loadAbsViewHolder(classLoader)

        XposedBridge.hookMethod(onChangeStatus, object : XC_MethodHook() {


            override fun afterHookedMethod(param: MethodHookParam) {
                val viewHolder = field1.get(param.thisObject)
                val `object` = param.args[0]
                val waContact = WaContactWpp(`object`)
                val viewField =
                    ReflectionUtils.findFieldUsingFilter(absViewHolderClass) { field -> field.type == View::class.java }
                val view = viewField.get(viewHolder) as View?
                val userJid = waContact.userJid
                if (userJid.isNull) return

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
        var contactListeners: HashSet<OnContactItemListener> = HashSet()
    }
}
