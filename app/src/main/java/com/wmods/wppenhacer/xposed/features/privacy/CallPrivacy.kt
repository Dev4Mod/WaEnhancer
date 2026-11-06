package com.wmods.wppenhacer.xposed.features.privacy

import android.content.SharedPreferences
import android.os.Message
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Tasker
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType

class CallPrivacy(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private var mVoipManager: Any? = null

    override fun doHook() {
        val voipManagerClass = Unobfuscator.loadVoipManager(classLoader)
        voipManagerClass.resolve().constructor { }.hookAll {
            after {
                mVoipManager = instance
            }
        }

        val clazzVoip = WppCore.voipManagerClass
        val endCallMethod = clazzVoip.declaredMethods.first { it.name == "endCall" }
        val rejectCallMethod = clazzVoip.declaredMethods.first { it.name == "rejectCall" }

        val onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader)
        val callPrivacyType = xprefs.getString("call_privacy", "0")!!.toInt()
        val callRejectType = xprefs.getString("call_type", null) ?: "no_internet"

        onCallReceivedMethod.hook {
            before {
                val callInfoClass = WppCore.voipCallInfoClass
                val callinfo: Any? = when {
                    args[0] is Message -> (args[0] as Message).obj
                    args.size > 1 && callInfoClass.isInstance(args[1]) -> args[1]
                    else -> {
                        Utils.showToast("Invalid call info", Toast.LENGTH_SHORT)
                        return@before
                    }
                }
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return@before
                if (ReflectionUtils.getObjectField(callinfo, "callState")
                        ?.toString() != "RECEIVED_CALL"
                ) return@before
                val userJid =
                    FMessageWpp.UserJid(ReflectionUtils.callMethod(callinfo, "getPeerJid"))
                val callId = ReflectionUtils.callMethod(callinfo, "getCallId")
                val waContact = WaContactWpp.getWaContactFromJid(userJid)
                val contactName = waContact?.displayName ?: userJid.phoneNumber
                Tasker.sendTaskerEvent(
                    contactName,
                    userJid.phoneNumber,
                    "call_received"
                )

                val privacyType = PrivacyType.getByValue(callPrivacyType)
                val blockCall = checkCallBlock(userJid, privacyType)
                if (!blockCall) return@before

                var rejectType = callRejectType
                val rejectMethod = RejectMethod.fromKey(rejectType)
                val fromCustomPrivacy = CustomPrivacy.getJSON(userJid).has("BlockCall")

                when (rejectType) {
                    "uncallable", "declined", "busy" -> {
                        if (rejectType == "declined") {
                            rejectType = ""
                        }
                        val params = ReflectionUtils.initArray(rejectCallMethod.parameterTypes)
                        params[0] = callId
                        params[1] = rejectType
                        ReflectionUtils.callMethod(rejectCallMethod, mVoipManager, *params)
                        result = true
                        notifyCallBlocked(contactName, privacyType, rejectMethod, fromCustomPrivacy)
                    }

                    "ended" -> {
                        val params = ReflectionUtils.initArray(endCallMethod.parameterTypes)
                        params[0] = true
                        ReflectionUtils.callMethod(endCallMethod, mVoipManager, *params)
                        result = true
                        notifyCallBlocked(contactName, privacyType, rejectMethod, fromCustomPrivacy)
                    }
                }
            }
        }

        WppCore.voipManagerClass.resolve().method {
            name = "nativeHandleIncomingXmppOffer"
        }.hookAll {
            before {
                val jidClass = Unobfuscator.findFirstClassUsingName(
                    classLoader, StringMatchType.EndsWith, "jid.Jid"
                )
                val jidObj = ReflectionUtils.getArg(args, jidClass, 0)
                val userJid = FMessageWpp.UserJid(jidObj)
                if (callRejectType == "no_internet") {
                    val privacyType = PrivacyType.getByValue(callPrivacyType)
                    val block = checkCallBlock(userJid, privacyType)
                    if (block) {
                        result = 1
                        val contactName = WaContactWpp.getWaContactFromJid(userJid)?.displayName
                            ?: userJid.phoneNumber
                        notifyCallBlocked(
                            contactName,
                            privacyType,
                            RejectMethod.NO_INTERNET,
                            CustomPrivacy.getJSON(userJid).has("BlockCall")
                        )
                    }
                }
            }
        }
    }


    private fun notifyCallBlocked(
        contactName: String?,
        privacyType: PrivacyType?,
        rejectMethod: RejectMethod?,
        fromCustomPrivacy: Boolean
    ) {
        if (!xprefs.getBoolean("call_block_notification_enabled", true)) return
        runCatching {
            val typeLabel = privacyType?.let { Utils.getString(it.labelRes) } ?: "?"
            val methodLabel = rejectMethod?.let { Utils.getString(it.labelRes) } ?: "?"
            val source = Utils.getString(
                if (fromCustomPrivacy) R.string.custom_privacy
                else R.string.call_blocked_source_global
            )
            Utils.showNotification(
                Utils.getString(R.string.call_blocked_notification_title),
                "${contactName ?: "?"} | $typeLabel | $methodLabel | $source",
                R.drawable.ic_notification_call_blocked
            )
        }.onFailure { logDebug(it) }
    }

    fun checkCallBlock(userJid: FMessageWpp.UserJid, type: PrivacyType?): Boolean {
        val customprivacy = CustomPrivacy.getJSON(userJid)
        if (customprivacy.has("BlockCall")) return customprivacy.optBoolean("BlockCall", false)

        return when (type) {
            PrivacyType.ALL_BLOCKED -> true
            PrivacyType.ALL_PERMITTED -> false
            PrivacyType.ONLY_UNKNOWN -> {
                val waContact = WaContactWpp.getWaContactFromJid(userJid) ?: return true
                !waContact.isSavedContact()
            }

            PrivacyType.BACKLIST -> {
                val callBlockList = xprefs.getString("call_block_contacts", "[]")!!
                val blockList = callBlockList.substring(1, callBlockList.length - 1).split(", ")
                    .map { it.trim() }
                val callerJids = setOfNotNull(userJid.phoneRawString, userJid.userRawString)
                blockList.any { it.isNotEmpty() && it in callerJids }
            }

            PrivacyType.WHITELIST -> {
                val callWhiteList = xprefs.getString("call_white_contacts", "[]")!!
                val whiteList = callWhiteList.substring(1, callWhiteList.length - 1).split(", ")
                    .map { it.trim() }
                val callerJids = setOfNotNull(userJid.phoneRawString, userJid.userRawString)
                whiteList.none { it.isNotEmpty() && it in callerJids }
            }

            null -> false
        }
    }

    override fun getPluginName() = "Call Privacy"

    enum class PrivacyType(val value: Int, val labelRes: Int) {
        ALL_PERMITTED(0, R.string.call_blocker_buttons_no_block), ALL_BLOCKED(
            1,
            R.string.call_blocker_buttons_all_block
        ),
        ONLY_UNKNOWN(2, R.string.call_blocker_buttons_only_unknown_block), BACKLIST(
            3,
            R.string.contact_block_list
        ),
        WHITELIST(4, R.string.contact_white_list);

        companion object {
            fun getByValue(value: Int) = entries.find { it.value == value }
        }
    }

    enum class RejectMethod(val key: String, val labelRes: Int) {
        NO_INTERNET("no_internet", R.string.call_type_buttons_no_internet),
        BUSY("busy", R.string.call_type_buttons_busy),
        UNCALLABLE("uncallable", R.string.call_type_buttons_uncallable),
        ENDED("ended", R.string.call_type_buttons_ended);

        companion object {
            fun fromKey(key: String) = entries.find { it.key == key }
        }
    }

}