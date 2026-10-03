package com.wmods.wppenhacer.xposed.features.general

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.net.toUri
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.SharedPreferencesWrapper
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache

class CallType(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {
    override fun doHook() {
        if (!xprefs.getBoolean("calltype", false)) return

        SharedPreferencesWrapper.addHook { key, value ->
            if (key == "call_confirmation_dialog_count") {
                99
            } else {
                value
            }
        }

        val startCallMethod = Unobfuscator.loadStartOutgoingCallMethod(classLoader)

        startCallMethod.hook {
            before {
                val context = args[0] as? Context ?: return@before
                val contactObj = args[1] ?: return@before
                val isVideo = args[3] as? Boolean ?: false
                if (isVideo) return@before

                val waContact = WaContactWpp(contactObj)
                val userJid = waContact.userJid
                val phoneNumber = userJid.phoneNumber
                if (phoneNumber.isNullOrEmpty()) return@before
                val originalArgs = args.copyOf()
                result = null
                val mAlertDialog = AlertDialogWpp(context)
                mAlertDialog.setTitle(UnobfuscatorCache.getInstance().getString("selectcalltype"))
                mAlertDialog.setItems(
                    arrayOf(
                        context.getString(R.string.phone_call),
                        context.getString(R.string.whatsapp_call)
                    )
                ) { dialog: DialogInterface?, which: Int ->
                    dialog?.dismiss()
                    when (which) {
                        0 -> {
                            val intent = Intent()
                            intent.action = Intent.ACTION_DIAL
                            intent.data = ("tel:+" + userJid.phoneNumber).toUri()
                            context.startActivity(intent)
                        }

                        1 -> {
                            invokeOriginal(*originalArgs)
                        }
                    }
                }
                mAlertDialog.show()
            }
        }
    }

    override fun getPluginName(): String {
        return "Call Type"
    }
}
