package com.wmods.wppenhacer.xposed.features.general

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge

class Tasker(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    override fun getPluginName(): String {
        return "Tasker"
    }

    @Throws(Throwable::class)
    override fun doHook() {
        val taskerEnabled = prefs.getBoolean("tasker", false)
        if (!taskerEnabled) return

        hookReceiveMessage()
        registerSenderMessage()
    }

    private fun registerSenderMessage() {
        val filter = IntentFilter("com.wmods.wppenhacer.MESSAGE_SENT")
        ContextCompat.registerReceiver(
            Utils.application, SenderMessageBroadcastReceiver(), filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    @Throws(Throwable::class)
    private fun hookReceiveMessage() {
        val method = Unobfuscator.loadReceiptMethod(classLoader)

        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[4] == "sender" || param.args[1] == null || param.args[3] == null) return
                val fMsg = FMessageWpp.Key(param.args[3]).fMessage ?: return
                val userJid = fMsg.key.remoteJid
                val name = WppCore.getContactName(userJid)
                val number = userJid.phoneNumber ?: return
                val msg = fMsg.messageStr ?: return
                if (TextUtils.isEmpty(msg) || userJid.isStatus) return
                Handler(Utils.application.mainLooper).post {
                    val intent = Intent("com.wmods.wppenhacer.MESSAGE_RECEIVED")
                    intent.putExtra("number", number)
                    intent.putExtra("name", name)
                    intent.putExtra("message", msg)
                    Utils.application.sendBroadcast(intent)
                }
            }
        })
    }

    companion object {
        @JvmStatic
        fun sendTaskerEvent(name: String?, number: String?, event: String) {
            val taskerEnabled = false
            if (!taskerEnabled) return

            val intent = Intent("com.wmods.wppenhacer.EVENT")
            intent.putExtra("name", name)
            intent.putExtra("number", number)
            intent.putExtra("event", event)
            Utils.application.sendBroadcast(intent)
        }
    }

    class SenderMessageBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            XposedBridge.log("Message sent")
            var number = intent.getStringExtra("number")
            if (number == null) {
                number = intent.getLongExtra("number", 0).toString()
                number = if (number == "0") null else number
            }
            val message = intent.getStringExtra("message")
            if (number == null || message == null) return
            number = number.replace("\\D".toRegex(), "")
            WppCore.sendMessage(number, message)
        }
    }
}
