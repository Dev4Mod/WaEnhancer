package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import android.os.Message
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.PropsHook
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType

class HideSeen(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val MEDIA_TYPE_VOICE_NOTE = 2
        private val CALL_RECEIPT_TAGS = setOf("offer", "accept", "reject", "enc_rekey")

        @JvmStatic
        fun generateFMessageKey(protocolTreeNodeWpp: ProtocolTreeNodeWpp): FMessageWpp.Key? {
            val fromKV = protocolTreeNodeWpp.attributes.first { it.key == "to" }
            val userJid = fromKV.userJid ?: return null
            val idKV = protocolTreeNodeWpp.attributes.first { it.key == "id" }
            return FMessageWpp.Key(idKV.value!!, userJid, false)
        }
    }

    private var hideReceipt = false
    private var hideReceiptUnknown = false
    private val ghostMode: Boolean
        get() = WppCore.getPrivBoolean("ghostmode", false)
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
        hookStanzaWriter()
    }

    private fun loadPreferences() {
        hideRead = xprefs.getBoolean("hideread", false)
        hideAudioSeen = xprefs.getBoolean("hideaudioseen", false)
        hideOnceSeen = xprefs.getBoolean("hideonceseen", false)
        hideReadGroup = xprefs.getBoolean("hideread_group", false)
        hideStatusView = xprefs.getBoolean("hidestatusview", false)
        hideReceipt = xprefs.getBoolean("hidereceipt", false)
        hideReceiptUnknown = xprefs.getBoolean("hidereceipt_unknown", false)
    }

    private fun hookStanzaWriter() {
        val stanzaWriterMethod = Unobfuscator.loadStanzaWriterMethod(classLoader)

        stanzaWriterMethod.hook {
            before {
                val node = args[0] ?: return@before
                if (!ProtocolTreeNodeWpp.TYPE.isInstance(node)) return@before
                val receipt = ProtocolTreeNodeWpp(node)
                if (receipt.tag != "receipt" || !revealsDelivery(receipt)) return@before

                val userJid =
                    receipt.attributes.firstOrNull { it.key == "to" }?.userJid ?: return@before
                val messageId = receipt.getFirstKeyValue("id") ?: return@before
                if (userJid.isNull || userJid.isStatus || userJid.isNewsletter) return@before

                val fmessageKey = FMessageWpp.Key(messageId, userJid, false)
                if (!shouldBlockReceipt(fmessageKey)) return@before

                result = null
                insertHiddenIfAbsent(fmessageKey)
            }
        }
    }

    private fun revealsDelivery(receipt: ProtocolTreeNodeWpp): Boolean {
        if (receipt.children.any { it.tag in CALL_RECEIPT_TAGS }) return false
        return when (receipt.getFirstKeyValue("type")) {
            null, "inactive" -> true
            else -> false
        }
    }

    private fun hookSendReadReceiptJob() {
        val sendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader)
        val sendJobClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "SendReadReceiptJob"
        )

        sendReadReceiptJobMethod.hook {
            before {
                val job = instance
                val hasBlueOnReply =
                    ReflectionUtils.getAdditionalInstanceField(job, "blue_on_reply") as? Boolean
                        ?: false

                if (!sendJobClass.isInstance(job) || hasBlueOnReply) return@before

                val lid = ReflectionUtils.getObjectField(job, "jid") as? String
                val isInvalidJid =
                    lid.isNullOrEmpty() || lid.contains("lid_me") || lid.contains("status_me")

                if (isInvalidJid) return@before

                val userJid = FMessageWpp.UserJid(lid)
                if (userJid.isNull) return@before

                val privacy = CustomPrivacy.getJSON(userJid)
                val isHide = processReadReceiptByType(this, job, userJid, privacy)

                if (isHide) {
                    recordHiddenMessages(job, userJid)
                }
            }
        }
    }

    private fun processReadReceiptByType(
        param: HookParam,
        job: Any,
        userJid: FMessageWpp.UserJid,
        privacy: JSONObject
    ): Boolean {
        val hideReceiptRead = checkPrivacyAndHideReceipt(userJid)
        return when {
            userJid.isGroup -> {
                if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode || hideReceiptRead) {
                    param.result = null
                    true
                } else false
            }

            userJid.isStatus -> {
                val participant = ReflectionUtils.getObjectField(job, "participant") as? String
                val statusJid = FMessageWpp.UserJid(participant)
                val customHideStatusView = CustomPrivacy.getJSON(statusJid)
                    .optBoolean("HideViewStatus", hideStatusView)

                if (customHideStatusView || ghostMode) {
                    param.result = null
                }
                false
            }

            else -> {
                if (privacy.optBoolean("HideSeen", hideRead) || ghostMode || hideReceiptRead) {
                    param.result = null
                    true
                } else false
            }
        }
    }

    private fun recordHiddenMessages(sendReadReceiptJob: Any, userJid: FMessageWpp.UserJid) {
        val messageIds =
            ReflectionUtils.getObjectField(sendReadReceiptJob, "messageIds") as? Array<*> ?: return
        val ids = messageIds.filterIsInstance<String>()
        if (ids.isEmpty()) return
        MessageHistoryStore.getInstance().insertHideSeenMessagesAsync(
            userJid.phoneRawString,
            userJid.userRawString,
            ids,
            MessageHistoryStore.ReceiptType.READ,
            false
        )
    }

    private fun hookReceiptMethod() {

        val receiptMethod = Unobfuscator.loadReceiptMethod(classLoader)
        val receiptMessageInfoClass = Unobfuscator.loadReceiptMessageInfoClass(classLoader)
        val onDispatchMessage = Unobfuscator.loadOndispatchMessage(classLoader)

        onDispatchMessage.forEach { method ->
            method.hook {
                before {
                    val message = args[0] as Message
                    val type = message.arg1
                    val obj = message.obj
                    when (type) {
                        419 -> {
                            if (!receiptMessageInfoClass.isInstance(obj)) return@before
                            val fmessageKeyField =
                                ReflectionUtils.findFieldUsingFilter(receiptMessageInfoClass) {
                                    FMessageWpp.Key.TYPE.isAssignableFrom(it.type)
                                }
                            val fmessageKey = FMessageWpp.Key(fmessageKeyField.get(obj))
                            blockReceiptDispatch(message, fmessageKey)
                        }

                        9 -> {
                            if (!FMessageWpp.TYPE.isInstance(obj)) return@before
                            blockReceiptDispatch(message, FMessageWpp(obj).key)
                        }
                    }
                }
            }
        }

        PropsHook.booleans[19148] = false // Change route IQ

        receiptMethod.hook {
            after {
                val protocolTreeNodeWpp = ProtocolTreeNodeWpp(result!!)

                val typeKV = protocolTreeNodeWpp.attributes.firstOrNull {
                    it.key == "type"
                }

                val fmessageKey = generateFMessageKey(protocolTreeNodeWpp) ?: return@after

                if (fmessageKey.remoteJid.isStatus) return@after

                val isReadType = typeKV?.value == "read"
                val blockSeen = shouldBlockSeen(fmessageKey, isReadType)
                val blockReceipt = shouldBlockReceipt(fmessageKey)

                if (!blockSeen && !blockReceipt) return@after

                if (blockReceipt) {
                    if (typeKV == null) {
                        protocolTreeNodeWpp.addKeyValue("type", "inactive")
                    } else {
                        typeKV.value = "inactive"
                    }
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                } else {
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("type")
                }

                insertHidden(fmessageKey)
            }
        }
    }

    private fun blockReceiptDispatch(message: Message, fmessageKey: FMessageWpp.Key) {
        if (!shouldBlockReceipt(fmessageKey)) return
        message.arg1 = -1
        insertHiddenIfAbsent(fmessageKey)
    }

    private fun insertHiddenIfAbsent(fmessageKey: FMessageWpp.Key) {
        if (getHiddenItem(fmessageKey) == null) insertHidden(fmessageKey)
    }

    private fun insertHidden(fmessageKey: FMessageWpp.Key) {
        MessageHistoryStore.getInstance().insertHideSeenMessageAsync(
            fmessageKey.remoteJid.phoneRawString,
            fmessageKey.remoteJid.userRawString,
            fmessageKey.messageID,
            MessageHistoryStore.ReceiptType.READ,
            false
        )
    }

    private fun getHiddenItem(fmessageKey: FMessageWpp.Key): MessageHistoryStore.MessageSeenItem? {
        return MessageHistoryStore.getInstance().getHideSeenMessage(
            fmessageKey.remoteJid.phoneRawString,
            fmessageKey.remoteJid.userRawString,
            fmessageKey.messageID,
            MessageHistoryStore.ReceiptType.READ
        )
    }

    private fun isAlreadyViewed(fmessageKey: FMessageWpp.Key): Boolean {
        return getHiddenItem(fmessageKey)?.viewed == true
    }

    private fun shouldBlockSeen(
        fmessageKey: FMessageWpp.Key,
        isReadType: Boolean = true
    ): Boolean {
        if (!isReadType || !checkPrivacyAndHideSeen(fmessageKey)) return false
        return !isAlreadyViewed(fmessageKey)
    }

    private fun shouldBlockReceipt(fmessageKey: FMessageWpp.Key): Boolean {
        if (!checkPrivacyAndHideReceipt(fmessageKey)) return false
        return !isAlreadyViewed(fmessageKey)
    }

    private fun isUnknownNumber(userJid: FMessageWpp.UserJid): Boolean {
        if (!hideReceiptUnknown || userJid.isGroup || userJid.isStatus || userJid.isNewsletter) return false
        return WaContactWpp.getWaContactFromJid(userJid)?.isSavedContact() == false
    }

    private fun checkPrivacyAndHideReceipt(userJid: FMessageWpp.UserJid): Boolean {
        val privacy = CustomPrivacy.getJSON(userJid)
        return privacy.optBoolean("HideReceipt", hideReceipt) || ghostMode || isUnknownNumber(userJid)
    }

    private fun checkPrivacyAndHideReceipt(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid)
        val customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt)
        return customHideReceipt || ghostMode || isUnknownNumber(fmessageKey.remoteJid)
    }

    private fun checkPrivacyAndHideSeen(fmessageKey: FMessageWpp.Key): Boolean {
        val privacy = CustomPrivacy.getJSON(fmessageKey.remoteJid)
        val hideKey = if (fmessageKey.remoteJid.isGroup) hideReadGroup else hideRead
        val shouldHide = privacy.optBoolean("HideSeen", hideKey) || ghostMode
        return shouldHide
    }

    private fun hookSenderPlayed() {
        val loadSenderPlayed = Unobfuscator.loadSenderPlayedBusiness(classLoader)

        loadSenderPlayed.hook {
            before {
                val set = args[0] as? Set<*>
                if (set.isNullOrEmpty()) return@before

                processSenderPlayed(this, set.filterNotNull().map { FMessageWpp(it) })
            }
        }
    }

    private fun processSenderPlayed(param: HookParam, messages: List<FMessageWpp>) {
        val hiddenMessages = messages.filter { fMessage ->
            val isHideViewOnce = (hideOnceSeen || ghostMode) && fMessage.isViewOnce
            val isHideVoiceNote =
                (hideAudioSeen || ghostMode) && fMessage.mediaType == MEDIA_TYPE_VOICE_NOTE
            isHideViewOnce || isHideVoiceNote
        }

        if (hiddenMessages.isNotEmpty()) {
            param.result = null
            for (fMessage in hiddenMessages) {
                val key = fMessage.key
                MessageHistoryStore.getInstance().insertHideSeenMessageAsync(
                    key.remoteJid.phoneRawString,
                    key.remoteJid.userRawString,
                    key.messageID,
                    MessageHistoryStore.ReceiptType.PLAYED,
                    false
                )
            }
            return
        }

        if (hideOnceSeen || ghostMode) return
        for (fMessage in messages) {
            if (!fMessage.isViewOnce) continue
            val key = fMessage.key
            val phoneRaw = key.remoteJid.phoneRawString
            val userRaw = key.remoteJid.userRawString
            val messageId = key.messageID
            MessageHistoryStore.getInstance().apply {
                updateViewedMessageAsync(
                    phoneRaw,
                    userRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.PLAYED,
                    true
                )
                updateViewedMessageAsync(
                    phoneRaw,
                    userRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.READ,
                    true
                )
            }
        }
    }


    override fun getPluginName(): String {
        return "Hide Seen"
    }
}
