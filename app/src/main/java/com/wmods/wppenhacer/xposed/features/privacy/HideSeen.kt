package com.wmods.wppenhacer.xposed.features.privacy

import android.os.Message
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType

class HideSeen(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val MEDIA_TYPE_VOICE_NOTE = 2

        @JvmStatic
        fun generateFMessageKey(protocolTreeNodeWpp: ProtocolTreeNodeWpp): FMessageWpp.Key? {
            val fromKV = protocolTreeNodeWpp.attributes.first { it.key == "to" }
            val userJid = fromKV.userJid ?: return null
            val idKV = protocolTreeNodeWpp.attributes.first { it.key == "id" }
            return FMessageWpp.Key(idKV.value!!, userJid, false)
        }
    }

    private var hideReceipt = false
    private var ghostMode = false
    private var hideRead = false
    private var hideAudioSeen = false
    private var hideOnceSeen = false
    private var hideReadGroup = false
    private var hideStatusView = false

    override fun doHook() {
        loadPreferences()
        hookSendReadReceiptJob()
        hookReceiptMethod()
        hookSenderPlayed()
        hookSenderPlayedBusiness()
    }

    private fun loadPreferences() {
        ghostMode = WppCore.getPrivBoolean("ghostmode", false)
        hideRead = prefs.getBoolean("hideread", false)
        hideAudioSeen = prefs.getBoolean("hideaudioseen", false)
        hideOnceSeen = prefs.getBoolean("hideonceseen", false)
        hideReadGroup = prefs.getBoolean("hideread_group", false)
        hideStatusView = prefs.getBoolean("hidestatusview", false)
        hideReceipt = prefs.getBoolean("hidereceipt", false)

    }

    private fun hookSendReadReceiptJob() {
        val sendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader)
        val sendJobClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "SendReadReceiptJob"
        )

        XposedBridge.hookMethod(sendReadReceiptJobMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val job = param.thisObject
                val hasBlueOnReply =
                    XposedHelpers.getAdditionalInstanceField(job, "blue_on_reply") as? Boolean
                        ?: false

                if (!sendJobClass.isInstance(job) || hasBlueOnReply) return

                val lid = XposedHelpers.getObjectField(job, "jid") as? String
                val isInvalidJid =
                    lid.isNullOrEmpty() || lid.contains("lid_me") || lid.contains("status_me")

                if (isInvalidJid) return

                val userJid = FMessageWpp.UserJid(lid)
                if (userJid.isNull) return

                val privacy = CustomPrivacy.getJSON(userJid.phoneNumber)
                val isHide = processReadReceiptByType(param, job, userJid, privacy)

                if (isHide) {
                    recordHiddenMessages(job, userJid)
                }
            }
        })
    }

    private fun processReadReceiptByType(
        param: XC_MethodHook.MethodHookParam,
        job: Any,
        userJid: FMessageWpp.UserJid,
        privacy: JSONObject
    ): Boolean {
        return when {
            userJid.isGroup -> {
                if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode) {
                    param.result = null
                    true
                } else false
            }

            userJid.isStatus -> {
                val participant = XposedHelpers.getObjectField(job, "participant") as? String
                val statusJid = FMessageWpp.UserJid(participant)
                val customHideStatusView = CustomPrivacy.getJSON(statusJid.phoneNumber)
                    .optBoolean("HideViewStatus", hideStatusView)

                if (customHideStatusView || ghostMode) {
                    param.result = null
                }
                false
            }

            else -> {
                if (privacy.optBoolean("HideSeen", hideRead) || ghostMode) {
                    param.result = null
                    true
                } else false
            }
        }
    }

    private fun recordHiddenMessages(sendReadReceiptJob: Any, userJid: FMessageWpp.UserJid) {
        val messageIds =
            XposedHelpers.getObjectField(sendReadReceiptJob, "messageIds") as? Array<*> ?: return
        for (messageId in messageIds) {
            MessageHistoryStore.getInstance().insertHideSeenMessage(
                userJid.phoneRawString,
                messageId as String?,
                MessageHistoryStore.ReceiptType.READ,
                false
            )
        }
    }

    private fun hookReceiptMethod() {

        val receiptMethod = Unobfuscator.loadReceiptMethod(classLoader)
        val receiptMainCallerMethod = Unobfuscator.loadReceiptMainCallerMethod(classLoader);
        val receiptCallerMethods = Unobfuscator.loadReceiptCallersMethod(classLoader);
        val hookCallerMethod = object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam) {
                val firstArg = param.args[0] as? Message ?: return
                if (firstArg.arg1 != 419 && firstArg.arg1 != 89)return
                val obj = firstArg.obj
                val checkResult = receiptMainCallerMethod.invoke(null, obj);
                if (checkResult == null)
                    param.result = null;
            }
        }
        receiptCallerMethods.forEach { XposedBridge.hookMethod(it, hookCallerMethod) }

        XposedBridge.hookMethod(receiptMethod, object : XC_MethodHook() {

            override fun afterHookedMethod(param: MethodHookParam) {

                val protocolTreeNodeWpp = ProtocolTreeNodeWpp(param.result)

                val typeKV = protocolTreeNodeWpp.attributes.firstOrNull {
                    it.key == "type"
                }

                val fmessageKey = generateFMessageKey(protocolTreeNodeWpp) ?: return

                val hideSeenItem = MessageHistoryStore.getInstance().getHideSeenMessage(
                    fmessageKey.remoteJid.phoneRawString,
                    fmessageKey.messageID,
                    MessageHistoryStore.ReceiptType.READ
                )

                if (hideSeenItem?.viewed ?: false) return

                hideSeenItem?.let {
                    param.result = null
                    return
                }

                val hideSeen = checkPrivacyAndHideSeen(fmessageKey)
                val hideReceipt = checkPrivacyAndHideReceipt(fmessageKey)

                if (hideReceipt) {
                    if (typeKV == null) {
                        protocolTreeNodeWpp.addKeyValue("type", "inactive")
                    } else {
                        typeKV.value = "inactive"
                    }
                } else if (hideSeen && typeKV?.value == "read") {
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("type")
                }


                if (hideReceipt || hideSeen) {
                    MessageHistoryStore.getInstance().insertHideSeenMessage(
                        fmessageKey.remoteJid.phoneRawString,
                        fmessageKey.messageID,
                        MessageHistoryStore.ReceiptType.READ,
                        false
                    )
                }
            }
        })
    }

    private fun checkPrivacyAndHideReceipt(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid.phoneNumber)
        val customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt)
        return customHideReceipt || ghostMode
    }

    private fun checkPrivacyAndHideSeen(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid.phoneNumber)
        val hideKey = if (fmessageKey.remoteJid.isGroup) hideReadGroup else hideRead
        val shouldHide = privacy.optBoolean("HideSeen", hideKey) || ghostMode
        return shouldHide
    }

    private fun hookSenderPlayed() {
        val loadSenderPlayed = Unobfuscator.loadSenderPlayedMethod(classLoader)

        XposedBridge.hookMethod(loadSenderPlayed, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val fMessage = FMessageWpp(param.args[0])
                processSenderPlayed(param, fMessage)
            }
        })
    }

    private fun hookSenderPlayedBusiness() {
        val loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader)

        XposedBridge.hookMethod(loadSenderPlayedBusiness, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val set = param.args[0] as? Set<*>
                if (set.isNullOrEmpty()) return

                val fMessage = FMessageWpp(set.first())
                processSenderPlayed(param, fMessage)
            }
        })
    }

    private fun processSenderPlayed(param: XC_MethodHook.MethodHookParam, fMessage: FMessageWpp) {
        val isHideViewOnce = (hideOnceSeen || ghostMode) && fMessage.isViewOnce
        val isHideVoiceNote =
            (hideAudioSeen || ghostMode) && fMessage.mediaType == MEDIA_TYPE_VOICE_NOTE
        val key = fMessage.key

        if (isHideViewOnce || isHideVoiceNote) {
            param.result = null
            MessageHistoryStore.getInstance().insertHideSeenMessage(
                key.remoteJid.phoneRawString,
                key.messageID,
                MessageHistoryStore.ReceiptType.PLAYED,
                false
            )
        }

        if (fMessage.isViewOnce && !hideOnceSeen && !ghostMode) {
            val phoneRaw = key.remoteJid.phoneRawString
            val messageId = key.messageID
            MessageHistoryStore.getInstance().apply {
                updateViewedMessage(
                    phoneRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.PLAYED,
                    true
                )
                updateViewedMessage(phoneRaw, messageId, MessageHistoryStore.ReceiptType.READ, true)
            }
        }
    }


    override fun getPluginName(): String {
        return "Hide Seen"
    }
}