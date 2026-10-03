package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import android.os.Message
import com.highcapable.yukihookapi.hook.param.HookParam
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.ProtocolTreeNodeWpp
import com.wmods.wppenhacer.xposed.core.db.MessageHistoryStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import org.json.JSONObject
import org.luckypray.dexkit.query.enums.StringMatchType

class HideSeen(loader: ClassLoader, preferences: SharedPreferences) :
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
        hookEnforceHiding()
    }

    private fun loadPreferences() {
        ghostMode = WppCore.getPrivBoolean("ghostmode", false)
        hideRead = xprefs.getBoolean("hideread", false)
        hideAudioSeen = xprefs.getBoolean("hideaudioseen", false)
        hideOnceSeen = xprefs.getBoolean("hideonceseen", false)
        hideReadGroup = xprefs.getBoolean("hideread_group", false)
        hideStatusView = xprefs.getBoolean("hidestatusview", false)
        hideReceipt = xprefs.getBoolean("hidereceipt", false)

    }

    private fun hookEnforceHiding() {
        Unobfuscator.loadReadReceiptMethod(classLoader).hook {
            before {
                val fMessage = FMessageWpp(args[0])
                val fmessageKey = fMessage.key
                val hideReceipt = checkPrivacyAndHideReceipt(fmessageKey)
                if (hideReceipt) {
                    result = null
                    MessageHistoryStore.getInstance().insertHideSeenMessageAsync(
                        fmessageKey.remoteJid.phoneRawString,
                        fmessageKey.messageID,
                        MessageHistoryStore.ReceiptType.READ,
                        false
                    )
                }
            }
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

                val privacy = CustomPrivacy.getJSON(userJid.phoneNumber)
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
        return when {
            userJid.isGroup -> {
                if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode) {
                    param.result = null
                    true
                } else false
            }

            userJid.isStatus -> {
                val participant = ReflectionUtils.getObjectField(job, "participant") as? String
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
            ReflectionUtils.getObjectField(sendReadReceiptJob, "messageIds") as? Array<*> ?: return
        val ids = messageIds.filterIsInstance<String>()
        if (ids.isEmpty()) return
        MessageHistoryStore.getInstance().insertHideSeenMessagesAsync(
            userJid.phoneRawString,
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
                    if (type != 419 && type != 89) return@before
                    if (!receiptMessageInfoClass.isInstance(obj)) return@before
                    // We check if the message is duplicated to avoid sending a tick twice causing congestion in the IQ queue
                    val fmessageKeyField = ReflectionUtils.findFieldUsingFilter(obj.javaClass) {
                        FMessageWpp.Key.TYPE.isAssignableFrom(it.type)
                    }
                    val fmessageKey = FMessageWpp.Key(fmessageKeyField.get(obj))
                    val hideSeenItem = MessageHistoryStore.getInstance().getHideSeenMessage(
                        fmessageKey.remoteJid.phoneRawString,
                        fmessageKey.messageID,
                        MessageHistoryStore.ReceiptType.READ
                    )

                    if (hideSeenItem?.viewed ?: false) return@before

                    hideSeenItem?.let {
                        message.arg1 = -1
                        return@before
                    }
                }
            }
        }


        Others.propsBoolean[19148] = false // Change route IQ

        receiptMethod.hook {
            after {

                val protocolTreeNodeWpp = ProtocolTreeNodeWpp(result!!)

                val typeKV = protocolTreeNodeWpp.attributes.firstOrNull {
                    it.key == "type"
                }

                val fmessageKey = generateFMessageKey(protocolTreeNodeWpp) ?: return@after

                if (fmessageKey.remoteJid.isStatus) return@after

                val hideSeenItem = MessageHistoryStore.getInstance().getHideSeenMessage(
                    fmessageKey.remoteJid.phoneRawString,
                    fmessageKey.messageID,
                    MessageHistoryStore.ReceiptType.READ
                )

                if (hideSeenItem?.viewed ?: false) return@after

                val hideSeen = checkPrivacyAndHideSeen(fmessageKey)
                val hideReceipt = checkPrivacyAndHideReceipt(fmessageKey)

                if (hideReceipt) {
                    if (typeKV == null) {
                        protocolTreeNodeWpp.addKeyValue("type", "inactive")
                    } else {
                        typeKV.value = "inactive"
                    }
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                } else if (hideSeen && typeKV?.value == "read") {
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("sts")
                    protocolTreeNodeWpp.removeAllKeyValuesByKey("type")
                }

                if (hideReceipt || hideSeen) {
                    MessageHistoryStore.getInstance().insertHideSeenMessageAsync(
                        fmessageKey.remoteJid.phoneRawString,
                        fmessageKey.messageID,
                        MessageHistoryStore.ReceiptType.READ,
                        false
                    )
                }
            }
        }


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

        loadSenderPlayed.hook {
            before {
                val fMessage = FMessageWpp(args[0])
                processSenderPlayed(this, fMessage)
            }
        }
    }

    private fun hookSenderPlayedBusiness() {
        val loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader)

        loadSenderPlayedBusiness.hook {
            before {
                val set = args[0] as? Set<*>
                if (set.isNullOrEmpty()) return@before

                val fMessage = FMessageWpp(set.first())
                processSenderPlayed(this, fMessage)
            }
        }
    }

    private fun processSenderPlayed(param: HookParam, fMessage: FMessageWpp) {
        val isHideViewOnce = (hideOnceSeen || ghostMode) && fMessage.isViewOnce
        val isHideVoiceNote =
            (hideAudioSeen || ghostMode) && fMessage.mediaType == MEDIA_TYPE_VOICE_NOTE
        val key = fMessage.key

        if (isHideViewOnce || isHideVoiceNote) {
            param.result = null
            MessageHistoryStore.getInstance().insertHideSeenMessageAsync(
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
                updateViewedMessageAsync(
                    phoneRaw,
                    messageId,
                    MessageHistoryStore.ReceiptType.PLAYED,
                    true
                )
                updateViewedMessageAsync(
                    phoneRaw,
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
