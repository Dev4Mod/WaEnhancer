package com.wmods.wppenhacer.xposed.features.privacy;

import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.general.Tasker;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallPrivacy extends Feature {

    /**
     * @noinspection unchecked
     */
    @Override
    public void doHook() throws Throwable {

        var onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader);

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo = null;
                Class<?> callInfoClass = XposedHelpers.findClass("com.whatsapp.voipcalling.CallInfo", classLoader);
                if (param.args[0] instanceof Message) {
                    callinfo = ((Message) param.args[0]).obj;
                } else if (param.args.length > 1 && callInfoClass.isInstance(param.args[1])) {
                    callinfo = param.args[1];
                } else {
                    Utils.showToast("Invalid call info", Toast.LENGTH_SHORT);
                    return;
                }
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return;
                if ((boolean) XposedHelpers.callMethod(callinfo, "isCaller")) return;
                var userJid = XposedHelpers.callMethod(callinfo, "getPeerJid");
                var callId = XposedHelpers.callMethod(callinfo, "getCallId");
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                Tasker.sendTaskerEvent(WppCore.getContactName(userJid), WppCore.stripJID(WppCore.getRawString(userJid)), "call_received");
                var blockCall = checkCallBlock(userJid, PrivacyType.getByValue(type));
                if (!blockCall) return;
                var clazzVoip = XposedHelpers.findClass("com.whatsapp.voipcalling.Voip", classLoader);
                var rejectType = prefs.getString("call_type", "no_internet");
                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                        var rejectCallMethod = ReflectionUtils.findMethodUsingFilter(clazzVoip, m -> m.getName().equals("rejectCall"));
                        var params = ReflectionUtils.initArray(rejectCallMethod.getParameterTypes());
                        params[0] = callId;
                        params[1] = "declined".equals(rejectType) ? null : rejectType;
                        ReflectionUtils.callMethod(rejectCallMethod, null, params);
                        break;
                    case "ended":
                        try {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true);
                        } catch (NoSuchMethodError e) {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true, 0);
                        }
                        break;
                    default:
                }
            }
        });

        XposedBridge.hookAllMethods(classLoader.loadClass("com.whatsapp.voipcalling.Voip"), "nativeHandleIncomingXmppOffer", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!prefs.getString("call_type", "no_internet").equals("no_internet")) return;
                var userJid = param.args[0];
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                var block = checkCallBlock(userJid, PrivacyType.getByValue(type));
                if (block) {
                    param.setResult(1);
                }
            }
        });


    }


    public CallPrivacy(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public boolean checkCallBlock(Object userJid, PrivacyType type) throws IllegalAccessException, InvocationTargetException {
        var jid = WppCore.stripJID(WppCore.getRawString(userJid));
        logDebug("checkCallBlock: " + jid);
        logDebug("checkCallBlock: " + type);
        if (jid == null) return false;

        var customprivacy = CustomPrivacy.getJSON(jid);

        if (type == PrivacyType.ALL_BLOCKED) {
            return customprivacy.optBoolean("BlockCall", true);
        }

        if (type == PrivacyType.ALL_PERMITTED) {
            return customprivacy.optBoolean("BlockCall", false);
        }

        if (type == PrivacyType.ONLY_UNKNOWN && customprivacy.optBoolean("BlockCall", false)) {
            return true;
        }

        switch (type) {
            case ONLY_UNKNOWN:
                jid += "@s.whatsapp.net";
                var contactName = WppCore.getSContactName(WppCore.createUserJid(jid), true);
                logDebug("contactName: " + contactName);
                return TextUtils.isEmpty(contactName) || contactName.equals(jid);
            case BACKLIST:
                var callBlockList = prefs.getString("call_block_contacts", "[]");
                var blockList = Arrays.stream(callBlockList.substring(1, callBlockList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var blockNumber : blockList) {
                    if (!TextUtils.isEmpty(blockNumber) && jid.contains(blockNumber)) {
                        return true;
                    }
                }
                return false;
            case WHITELIST:
                var callWhiteList = prefs.getString("call_white_contacts", "[]");
                var whiteList = Arrays.stream(callWhiteList.substring(1, callWhiteList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var whiteNumber : whiteList) {
                    if (!TextUtils.isEmpty(whiteNumber) && jid.contains(whiteNumber)) {
                        return false;
                    }
                }
                return true;
        }
        return false;
    }

    public enum PrivacyType {
        ALL_PERMITTED(0),
        ALL_BLOCKED(1),
        ONLY_UNKNOWN(2),
        BACKLIST(3),
        WHITELIST(4);

        private final int value;

        PrivacyType(int i) {
            this.value = i;
        }

        public static PrivacyType getByValue(int value) {
            for (PrivacyType type : PrivacyType.values()) {
                if (type.value == value) {
                    return type;
                }
            }
            return null;
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Privacy";
    }
}
