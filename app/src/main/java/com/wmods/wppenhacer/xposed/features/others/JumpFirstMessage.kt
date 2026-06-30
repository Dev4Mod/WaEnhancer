package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.getCurrentActivity
import com.wmods.wppenhacer.xposed.core.WppCore.getCurrentUserJid
import com.wmods.wppenhacer.xposed.core.db.MessageStore.Companion.getInstance
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.findFirstClassUsingName
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadOnCreatedMenuConversation
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.enums.StringMatchType

class JumpFirstMessage(classLoader: ClassLoader, preferences:SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {
        if (!prefs.getBoolean("jump_first_message", false)) return
        val onCreateMenuConversationMethod = loadOnCreatedMenuConversation(classLoader)
        XposedBridge.hookMethod(onCreateMenuConversationMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val menu = param.args[0] as Menu
                    if (menu.findItem(R.string.jump_first_message) != null) {
                        return
                    }
                    val menuItem =
                        menu.add(0, R.string.jump_first_message, 0, R.string.jump_first_message)
                    menuItem.setOnMenuItemClickListener {
                        val activity =
                            getCurrentActivity() ?: return@setOnMenuItemClickListener false
                        jumpToFirstMessage(activity)
                        true
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        })
    }

    private fun jumpToFirstMessage(activity: Activity) {
        val userJid = getCurrentUserJid()
        if (userJid == null || userJid.isNull) {
            return
        }

        var rawJid = userJid.phoneRawString
        if (rawJid.isNullOrEmpty()) {
            rawJid = userJid.userRawString
        }
        if (rawJid.isNullOrEmpty()) {
            return
        }

        val firstMessageInfo = getInstance().getFirstMessageInfoByChatRawJid(rawJid) ?: return

        try {
            val conversationClass =
                findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "Conversation")
            val intent = Intent(activity, conversationClass)
            intent.putExtra("jid", rawJid)
            intent.putExtra("sort_id", firstMessageInfo.sortId)
            intent.putExtra("row_id", firstMessageInfo.rowId)
            intent.putExtra("start_t", SystemClock.uptimeMillis())
            intent.putExtra("mat_entry_point", 64)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            activity.startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        } catch (e: Exception) {
            logDebug(e)
        }
    }

    override fun getPluginName(): String {
        return "Jump First Message"
    }
}