package com.wmods.wppenhacer.xposed.features.general

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class CaptureDevice(classLoader: ClassLoader, xprefs: SharedPreferences) : Feature(
    classLoader,
    xprefs,
) {

    enum class DeviceType(val value: Int) {
        PHONE(0),
        LINKED_DEVICE(1);

        companion object {
            fun fromInt(value: Int): DeviceType {
                return if (value == PHONE.value) PHONE else LINKED_DEVICE
            }
        }
    }

    override fun doHook() {
        if (!prefs.getBoolean("capture_device", false)) return

        val handlePlaintextMethod =
            Unobfuscator.loadSharedMessageProcessorHandlePlaintextMethod(classLoader)
        XposedBridge.hookMethod(handlePlaintextMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    captureDeviceInfo(param.args.firstOrNull())
                } catch (t: Throwable) {
                    logDebug(t)
                }
            }
        })

        ConversationItemListener.conversationListeners.add(
            object : ConversationItemListener.OnConversationItemListener() {
                override fun onItemBind(
                    fMessage: FMessageWpp,
                    view: ViewGroup,
                    position: Int,
                    convertView: View?
                ) {
                    updateDeviceIndicator(fMessage, view)
                }
            }
        )
    }

    private fun captureDeviceInfo(firstArg: Any?) {
        if (firstArg == null) return

        val fMessage = findFMessage(firstArg) ?: return
        val deviceJid = fMessage.deviceJid ?: return
        val device = (XposedHelpers.callMethod(deviceJid, "getDevice") as? Number)?.toInt()
            ?: return
        val userjid = fMessage.key.remoteJid.userRawString ?: return
        val messageId = fMessage.key.messageID
        if (messageId.isEmpty()) return

        val deviceType = if (device == DeviceType.PHONE.value) {
            DeviceType.PHONE
        } else {
            DeviceType.LINKED_DEVICE
        }

        MessageHistoryStore.getInstance().insertDeviceInfoAsync(
            userjid = userjid,
            messageId = messageId,
            deviceType = deviceType.value
        )
    }

    private fun findFMessage(firstArg: Any): FMessageWpp? {
        if (FMessageWpp.TYPE.isInstance(firstArg)) {
            return FMessageWpp(firstArg)
        }

        var currentClass: Class<*>? = firstArg.javaClass
        while (currentClass != null) {
            val field = currentClass.declaredFields.firstOrNull { field ->
                FMessageWpp.TYPE.isAssignableFrom(field.type)
            }
            if (field != null) {
                field.isAccessible = true
                return FMessageWpp(field.get(firstArg))
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun updateDeviceIndicator(fMessage: FMessageWpp, view: ViewGroup) {
        val deviceIndicator = view.findViewWithTag<ImageView>(DEVICE_TYPE_TAG)
        val messageId = fMessage.key.messageID
        val userjid = fMessage.key.remoteJid.userRawString
        val deviceTypeValue = MessageHistoryStore.getInstance().getDeviceType(
            userjid,
            messageId
        ) {
            ConversationItemListener.notifyDataSetChanged()
        }

        if (deviceTypeValue == null) {
            removeDeviceIndicator(deviceIndicator)
            return
        }

        val dateWrapper = view.findViewById<ViewGroup>(Utils.getID("date_wrapper", "id"))
        if (dateWrapper == null) {
            removeDeviceIndicator(deviceIndicator)
            return
        }

        val target = deviceIndicator ?: ImageView(dateWrapper.context).apply {
            tag = DEVICE_TYPE_TAG
        }
        if (target.parent !== dateWrapper) {
            (target.parent as? ViewGroup)?.removeView(target)
            dateWrapper.addView(target, 0)
        }

        target.setImageResource(
            if (DeviceType.fromInt(deviceTypeValue) == DeviceType.PHONE) {
                R.drawable.device
            } else {
                R.drawable.linked_device
            }
        )
    }

    private fun removeDeviceIndicator(indicator: ImageView?) {
        (indicator?.parent as? ViewGroup)?.removeView(indicator)
    }

    override fun getPluginName(): String = "Capture Device"

    companion object {
        private const val DEVICE_TYPE_TAG = "device_type_text_view"
    }
}
