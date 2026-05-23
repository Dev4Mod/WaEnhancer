package com.wmods.wppenhacer.xposed.features.privacy

import android.os.Message
import android.widget.Toast
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Tasker
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.ConcurrentHashMap

class CallPrivacy(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    private var mVoipManager: Any? = null

    override fun doHook() {
        val voipManagerClass = Unobfuscator.loadVoipManager(classLoader)
        XposedBridge.hookAllConstructors(voipManagerClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                mVoipManager = param.thisObject
            }
        })

        val clazzVoip = WppCore.getVoipManagerClass(classLoader)
        val endCallMethod = clazzVoip.declaredMethods.first { it.name == "endCall" }
        val rejectCallMethod = clazzVoip.declaredMethods.first { it.name == "rejectCall" }

        val onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader)

        XposedBridge.hookMethod(onCallReceivedMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val callInfoClass = WppCore.getVoipCallInfoClass(classLoader)
                val callinfo: Any? = when {
                    param.args[0] is Message -> (param.args[0] as Message).obj
                    param.args.size > 1 && callInfoClass.isInstance(param.args[1]) -> param.args[1]
                    else -> {
                        Utils.showToast("Invalid call info", Toast.LENGTH_SHORT)
                        return
                    }
                }
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return
                if (XposedHelpers.getObjectField(callinfo, "callState")
                        ?.toString() != "RECEIVED_CALL"
                ) return
                val userJid = FMessageWpp.UserJid(XposedHelpers.callMethod(callinfo, "getPeerJid"))
                val callId = XposedHelpers.callMethod(callinfo, "getCallId")
                val type = prefs.getString("call_privacy", "0")!!.toInt()
                val waContact = WaContactWpp.getWaContactFromJid(userJid)
                val contactName = waContact?.displayName ?: userJid.phoneNumber
                Tasker.sendTaskerEvent(
                    contactName,
                    userJid.phoneNumber,
                    "call_received"
                )

                val privacyType = PrivacyType.getByValue(type)
                val blockCall = checkCallBlock(userJid, privacyType)
                if (!blockCall) return

                var rejectType =  prefs.getString("call_type", null) ?: "no_internet"

                when (rejectType) {
                    "uncallable", "declined", "busy" -> {
                        if (rejectType == "declined") {
                            rejectType = ""
                        }
                        val params = ReflectionUtils.initArray(rejectCallMethod.parameterTypes)
                        params[0] = callId
                        params[1] = rejectType
                        ReflectionUtils.callMethod(rejectCallMethod, mVoipManager, *params)
                        param.result = true
                    }
                    "ended" -> {
                        val params = ReflectionUtils.initArray(endCallMethod.parameterTypes)
                        params[0] = true
                        ReflectionUtils.callMethod(endCallMethod, mVoipManager, *params)
                        param.result = true
                    }
                }
            }
        })

        XposedBridge.hookAllMethods(
            WppCore.getVoipManagerClass(classLoader),
            "nativeHandleIncomingXmppOffer",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val jidClass = Unobfuscator.findFirstClassUsingName(
                        classLoader, StringMatchType.EndsWith, "jid.Jid"
                    )
                    val jidObj = ReflectionUtils.getArg(param.args, jidClass, 0)
                    val userJid = FMessageWpp.UserJid(jidObj)
                    val rejectType = prefs.getString("call_type", null) ?: "no_internet"
                    if (rejectType == "no_internet") {
                        val type = prefs.getString("call_privacy", "0")!!.toInt()
                        val privacyType = PrivacyType.getByValue(type)
                        val block = checkCallBlock(userJid, privacyType)
                        if (block) {
                            param.result = 1
                        }
                    }
                }
            })
    }


    fun checkCallBlock(userJid: FMessageWpp.UserJid, type: PrivacyType?): Boolean {
        val phoneNumber = userJid.phoneNumber ?: return false

        val customprivacy = CustomPrivacy.getJSON(phoneNumber);

        return when (type) {
            PrivacyType.ALL_BLOCKED -> customprivacy.optBoolean("BlockCall", true)
            PrivacyType.ALL_PERMITTED -> customprivacy.optBoolean("BlockCall", false)
            PrivacyType.ONLY_UNKNOWN -> {
                val waContact = WaContactWpp.getWaContactFromJid(userJid) ?: return true
                !waContact.isSavedContact()
            }
            PrivacyType.BACKLIST -> {
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                val callBlockList = prefs.getString("call_block_contacts", "[]")!!
                val blockList = callBlockList.substring(1, callBlockList.length - 1).split(", ")
                    .map { it.trim() }
                blockList.any { it.isNotEmpty() && it == userJid.phoneRawString }
            }

            PrivacyType.WHITELIST -> {
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                val callWhiteList = prefs.getString("call_white_contacts", "[]")!!
                val whiteList = callWhiteList.substring(1, callWhiteList.length - 1).split(", ")
                    .map { it.trim() }
                whiteList.none { it.isNotEmpty() && it == userJid.phoneRawString }
            }

            null -> false
        }
    }

    override fun getPluginName() = "Call Privacy"

    enum class PrivacyType(val value: Int) {
        ALL_PERMITTED(0), ALL_BLOCKED(
            1
        ),
        ONLY_UNKNOWN(2), BACKLIST(
            3
        ),
        WHITELIST(4);

        companion object {
            fun getByValue(value: Int) = entries.find { it.value == value }
        }
    }

}