package com.wmods.wppenhacer.xposed.features.general

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FStatusWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.listeners.MenuStatusListener
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class AntiRevoke(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private val messageRevokedMap = ConcurrentHashMap<String, MutableSet<String>>()

        private val dateFormatThreadLocal = ThreadLocal.withInitial {
            DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Utils.getApplication().resources.configuration.locales[0]
            )
        }

        private fun findObjectFMessage(param: XC_MethodHook.MethodHookParam): FMessageWpp? {
            val safeArgs = param.args?.filterNotNull() ?: return null
            safeArgs.firstOrNull { FMessageWpp.TYPE.isInstance(it) }?.let { return FMessageWpp(it) }
            val arg0 = param.args?.getOrNull(0) ?: return null
            return MenuStatusListener.getFMessageFromStatusData(arg0)
        }


        private fun getRevokedMessagesForJid(fMessage: FMessageWpp): MutableSet<String> {
            val stripJID =
                fMessage.key.remoteJid.phoneNumber ?: return Collections.synchronizedSet(HashSet())
            return messageRevokedMap.getOrPut(stripJID) {
                val messages =
                    DelMessageStore.getInstance(Utils.getApplication()).getMessagesByJid(stripJID)
                Collections.synchronizedSet(messages ?: HashSet())
            }
        }

        private fun persistRevokedMessage(fMessage: FMessageWpp, messageID: String) {
            val stripJID = fMessage.key.remoteJid.phoneNumber!!
            val messages = getRevokedMessagesForJid(fMessage)
            messages.add(messageID)
            DelMessageStore.getInstance(Utils.getApplication()).insertMessage(
                stripJID,
                messageID,
                System.currentTimeMillis()
            )
        }
    }

    override fun doHook() {
        val antiRevokeMessageMethod = Unobfuscator.loadAntiRevokeMessageMethod(classLoader)
        val unknownStatusPlaybackMethod = Unobfuscator.loadUnknownStatusPlaybackMethod(classLoader)
        val statusPlaybackClass = Unobfuscator.loadStatusPlaybackViewClass(classLoader)
        val antiRevokeFStatusMethod = Unobfuscator.loadAntiRevokeFStatusMethod(classLoader)

        XposedBridge.hookMethod(antiRevokeFStatusMethod, object : XC_MethodHook() {

            override fun beforeHookedMethod(param: MethodHookParam) {
                val fStatusKey = FStatusWpp.FStatusKey(param.args[1])
                val fstatus = fStatusKey.fStatus
                if (fstatus == null) return
                val fMessage = fstatus.fMessage
                if (fMessage == null) return
                if (!fStatusKey.isFromMe && handleRevocationAttempt(
                        fMessage,
                        fStatusKey.messageID
                    ) != 0
                ) {
                    param.result = 0
                }
            }

        })

        XposedBridge.hookMethod(antiRevokeMessageMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val args = param.args ?: return
                val fMessageObj = ReflectionUtils.getArg(args, FMessageWpp.TYPE, 0)
                if (fMessageObj == null) {
                    logDebug("FMessageObj is null in revoke!")
                    return
                }
                val fMessage = FMessageWpp(fMessageObj)
                val messageKey = fMessage.key
                val deviceJid = fMessage.deviceJid
                val messageId = XposedHelpers.getObjectField(fMessage.getObject(), "A01") as String


                if (messageKey.remoteJid.isGroup) {
                    if (deviceJid != null && handleRevocationAttempt(fMessage, messageId) != 0) {
                        param.result = true
                    }
                } else if (!messageKey.isFromMe && handleRevocationAttempt(
                        fMessage,
                        messageId
                    ) != 0
                ) {
                    param.result = true
                }
            }
        })

        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                viewGroup: ViewGroup,
                position: Int,
                convertView: View?
            ) {
                val dateTextView = viewGroup.findViewById<TextView>(Utils.getID("date", "id"))
                bindRevokedMessageUI(fMessage, dateTextView, "antirevoke")
            }
        })

        XposedBridge.hookMethod(unknownStatusPlaybackMethod, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = ReflectionUtils.getArg(param.args, param.method.declaringClass, 0)
                val fMessage = findObjectFMessage(param)
                val field =
                    ReflectionUtils.getFieldByType(param.method.declaringClass, statusPlaybackClass)

                if (obj == null || field == null || fMessage == null) {
                    logDebug("Invalid parameters")
                    return
                }

                val objView = field.get(obj) ?: return
                val textViews =
                    ReflectionUtils.getFieldsByType(statusPlaybackClass, TextView::class.java)

                if (textViews.isEmpty()) {
                    logDebug("No text views found")
                    return
                }

                val dateId = Utils.getID("date", "id")
                for (textViewField in textViews) {
                    val textView = textViewField.get(objView) as? TextView
                    if (textView != null && textView.id == dateId) {
                        bindRevokedMessageUI(fMessage, textView, "antirevokestatus")
                        break
                    }
                }
            }
        })
    }

    private fun bindRevokedMessageUI(
        fMessage: FMessageWpp,
        dateTextView: TextView?,
        antirevokeType: String
    ) {
        if (dateTextView == null) return

        val key = fMessage.key
        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        val originalMessage =
            XposedHelpers.getAdditionalInstanceField(dateTextView, "originalMessage") as? String

        val messageID = if (messageRevokedList.contains(key.messageID)) {
            key.messageID
        } else {
            MessageStore.getInstance().getOriginalMessageKey(fMessage.rowId)
                .takeIf { messageRevokedList.contains(it) }
        }

        if (messageID != null) {
            val appInstance = Utils.getApplication()
            val timestamp =
                DelMessageStore.getInstance(appInstance).getTimestampByMessageId(messageID)
            if (timestamp > 0) {
                val date = dateFormatThreadLocal.get()?.format(Date(timestamp))
                dateTextView.paint.isUnderlineText = true
                dateTextView.setOnClickListener {
                    val toastMessage =
                        Utils.getApplication().getString(R.string.message_removed_on)
                            .format(date)
                    Utils.showToast(toastMessage, Toast.LENGTH_LONG)
                }
            }

            val antirevokeValue = prefs.getString(antirevokeType, "0")?.toIntOrNull() ?: 0

            when (antirevokeValue) {
                1 -> {
                    val messageText = originalMessage ?: dateTextView.text
                    val newTextData = "${
                        UnobfuscatorCache.getInstance().getString("messagedeleted")
                    } | $messageText"
                    dateTextView.text = newTextData
                    XposedHelpers.setAdditionalInstanceField(
                        dateTextView,
                        "originalMessage",
                        messageText.toString()
                    )
                }

                2 -> {
                    val drawable = Utils.getApplication().getDrawable(R.drawable.deleted)
                    dateTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
                    dateTextView.compoundDrawablePadding = 5
                }
            }
        } else {
            dateTextView.setCompoundDrawables(null, null, null, null)
            if (originalMessage != null) {
                dateTextView.text = originalMessage
            }
            dateTextView.paint.isUnderlineText = false
            dateTextView.setOnClickListener(null)
        }
    }

    private fun handleRevocationAttempt(fMessage: FMessageWpp, messageId: String): Int {
        try {
            handleRevocationAlert(fMessage)
        } catch (e: Exception) {
            log(e)
        }

        val revokeBoolean = prefs.getString(
            if (fMessage.key.remoteJid.isStatus) "antirevokestatus" else "antirevoke",
            "0"
        )?.toIntOrNull() ?: 0

        if (revokeBoolean == 0) return 0

        val messageRevokedList = getRevokedMessagesForJid(fMessage)
        if (!messageRevokedList.contains(messageId)) {
            CompletableFuture.runAsync {
                try {
                    persistRevokedMessage(fMessage, messageId)
                    val mConversation = WppCore.getCurrentConversation()
                    if (mConversation != null && fMessage.key.remoteJid.phoneNumber == WppCore.getCurrentUserJid()?.phoneNumber) {
                        mConversation.runOnUiThread {
                            if (mConversation.hasWindowFocus()) {
                                mConversation.startActivity(mConversation.intent)
                                @Suppress("DEPRECATION")
                                mConversation.overridePendingTransition(0, 0)
                                mConversation.window.decorView.findViewById<View>(android.R.id.content)
                                    .postInvalidate()
                            } else {
                                mConversation.recreate()
                            }
                        }
                    }
                } catch (e: Exception) {
                    logDebug(e)
                }
            }
        }
        return revokeBoolean
    }

    private fun formatRevocationMessage(fMessage: FMessageWpp): String? {
        var jidAuthor = fMessage.key.remoteJid
        var messageSuffix = Utils.getApplication().getString(R.string.deleted_message)

        if (jidAuthor.isStatus) {
            messageSuffix = Utils.getApplication().getString(R.string.deleted_status)
            jidAuthor = fMessage.userJid
        }
        val waContact = WaContactWpp.getWaContactFromJid(jidAuthor)

        val name = waContact?.displayName
            ?: jidAuthor.phoneNumber

        return if (jidAuthor.isGroup) {
            var participantJid = fMessage.userJid
            if (participantJid.isNull) {
                val deletedAdminUser = XposedHelpers.getObjectField(fMessage.getObject(), "A00")
                if (deletedAdminUser != null) {
                    participantJid = FMessageWpp.UserJid(deletedAdminUser)
                }
            }
            val participantWaContact = WaContactWpp.getWaContactFromJid(participantJid)

            val participantName = participantWaContact?.displayName
                ?: participantJid.phoneNumber

            Utils.getApplication()
                .getString(R.string.deleted_a_message_in_group, participantName, name)
        } else {
            "$name $messageSuffix"
        }
    }

    private fun handleRevocationAlert(fMessage: FMessageWpp) {
        val message = formatRevocationMessage(fMessage) ?: return

        val jidAuthor = fMessage.key.remoteJid
        val actualAuthor = if (jidAuthor.isStatus) fMessage.userJid else jidAuthor
        val waContact = WaContactWpp.getWaContactFromJid(actualAuthor)

        val name = waContact?.displayName ?: actualAuthor.phoneNumber

        val taskerAction = if (jidAuthor.isStatus) "deleted_status" else "deleted_message"

        if (prefs.getBoolean("toastdeleted", false)) {
            Utils.showToast(message, Toast.LENGTH_LONG)
        }

        Tasker.sendTaskerEvent(name, jidAuthor.phoneNumber, taskerAction)
    }

    override fun getPluginName(): String = "Anti Revoke"
}