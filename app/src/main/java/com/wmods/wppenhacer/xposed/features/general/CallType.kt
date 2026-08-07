package com.wmods.wppenhacer.xposed.features.general

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class CallType(loader: ClassLoader, preferences:SharedPreferences) :
    Feature(loader, preferences) {
    override fun doHook() {
        if (!prefs.getBoolean("calltype", false)) return

        SharedPreferencesWrapper.addHook { key, value ->
            if (key == "call_confirmation_dialog_count") {
                return@addHook 1
            }
            value
        }


        val callConfirmationFragment = XposedHelpers.findClass(
            "com.whatsapp.calling.fragment.CallConfirmationFragment",
            classLoader
        )
        val method = ReflectionUtils.findMethodUsingFilter(
            callConfirmationFragment
        ) { m -> m.parameterCount == 1 && m.parameterTypes[0] == Bundle::class.java }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val bundle = param.args.firstOrNull() as? Bundle ?: return
                param.setObjectExtra("wae_call_jid", bundle.getString("jid"))
                param.setObjectExtra("wae_is_video_call", bundle.getBoolean("is_video_call"))
            }

            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val jid = param.getObjectExtra("wae_call_jid") as? String
                val isVideoCall = param.getObjectExtra("wae_is_video_call") as? Boolean ?: false
                if (jid == null || isVideoCall) return
                val origDialog = param.result as Dialog
                val context = origDialog.context
                val mAlertDialog = AlertDialogWpp(origDialog.context)
                lateinit var newDialog: Dialog
                mAlertDialog.setTitle(UnobfuscatorCache.getInstance().getString("selectcalltype"))
                mAlertDialog.setItems(
                    arrayOf(
                        context.getString(R.string.phone_call),
                        context.getString(R.string.whatsapp_call)
                    )
                ) { _: DialogInterface?, which: Int ->
                    newDialog!!.dismiss()
                    when (which) {
                        0 -> {
                            val intent = Intent()
                            intent.action = Intent.ACTION_DIAL
                            val userJid = UserJid(jid)
                            intent.data = ("tel:+" + userJid.phoneNumber).toUri()
                            context.startActivity(intent)
                        }

                        1 -> origDialog.show()
                    }
                }
                newDialog = mAlertDialog.create()
                param.setResult(newDialog)
            }
        })
    }

    override fun getPluginName(): String {
        return "Call Type"
    }
}
