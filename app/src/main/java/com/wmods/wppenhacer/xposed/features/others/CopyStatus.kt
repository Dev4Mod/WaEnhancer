package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.getMethodDescriptor
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBlueOnReplayStatusViewMethod
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadBlueOnReplayViewButtonMethod
import com.wmods.wppenhacer.xposed.utils.Utils

class CopyStatus(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (!xprefs.getBoolean("copystatus", false)) return

        val viewButtonMethod = loadBlueOnReplayViewButtonMethod(classLoader)
        logDebug(getMethodDescriptor(viewButtonMethod))

        viewButtonMethod.hook {
            after {
                val view = result as View
                val caption = view.findViewById<View?>(Utils.getID("caption", "id")) as TextView?
                caption?.setOnLongClickListener {
                    Utils.setToClipboard(caption.text.toString())
                    Utils.showToast(
                        Utils.application.getString(R.string.copied_to_clipboard),
                        Toast.LENGTH_LONG
                    )
                    true
                }
            }
        }

        val viewStatusMethod = loadBlueOnReplayStatusViewMethod(classLoader)
        viewStatusMethod.hook {
            after {
                val view = args[0] as View
                val text = view.findViewById<View?>(Utils.getID("message_text", "id")) as TextView?
                text?.setOnLongClickListener {
                    Utils.setToClipboard(text.text.toString())
                    Utils.showToast(
                        Utils.application.getString(R.string.copied_to_clipboard),
                        Toast.LENGTH_LONG
                    )
                    true
                }
            }
        }
    }

    override fun getPluginName(): String {
        return "Copy Status"
    }
}
