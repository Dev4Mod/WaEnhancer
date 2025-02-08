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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
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

        var clazzVoip = XposedHelpers.findClass("com.whatsapp.voipcalling.Voip", classLoader);
        var endCallMethod = ReflectionUtils.findMethodUsingFilter(clazzVoip, m -> m.getName().equals("endCall"));
        var rejectCallMethod = ReflectionUtils.findMethodUsingFilter(clazzVoip, m -> m.getName().equals("rejectCall"));

        var onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader);

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo;
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
                var rejectType = prefs.getString("call_type", "no_internet");

                // Need Instance of VoipManager from WhatsApp 2.24.24.XX
                Object voipManager = null;
                if (!Modifier.isStatic(endCallMethod.getModifiers())) {
                    var fieldVoipManager = ReflectionUtils.findFieldUsingFilterIfExists(param.thisObject.getClass(), field -> clazzVoip.isInstance(ReflectionUtils.getObjectField(field, param.thisObject)));
                    voipManager = fieldVoipManager == null ? null : fieldVoipManager.get(param.thisObject);
                }
                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                        var params = ReflectionUtils.initArray(rejectCallMethod.getParameterTypes());
                        params[0] = callId;
                        params[1] = "declined".equals(rejectType) ? null : rejectType;
                        ReflectionUtils.callMethod(rejectCallMethod, voipManager, params);
                        param.setResult(true);
                        break;
                    case "ended":
                        var params1 = ReflectionUtils.initArray(endCallMethod.getParameterTypes());
                        params1[0] = true;
                        ReflectionUtils.callMethod(endCallMethod, voipManager, params1);
                        param.setResult(true);
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

    public boolean checkCallBlock(Object jid, PrivacyType type) throws IllegalAccessException, InvocationTargetException {
        var rawPhoneNumber = WppCore.getRawString(jid);
        rawPhoneNumber = rawPhoneNumber.replaceFirst("\\.[\\d:]+@", "@");
        var userJid = WppCore.createUserJid(rawPhoneNumber);

        var phoneNumber = WppCore.stripJID(rawPhoneNumber);

        if (phoneNumber == null) return false;

        var customprivacy = CustomPrivacy.getJSON(phoneNumber);

        if (type == PrivacyType.ALL_BLOCKED) {
            return customprivacy.optBoolean("BlockCall", true);
        }

        if (type == PrivacyType.ALL_PERMITTED) {
            return customprivacy.optBoolean("BlockCall", false);
        }

        switch (type) {
            case ONLY_UNKNOWN:
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                var contactName = WppCore.getSContactName(userJid, true);
                return TextUtils.isEmpty(contactName) || contactName.equals(phoneNumber);
            case BACKLIST:
                if (customprivacy.optBoolean("BlockCall", false)) return true;
                var callBlockList = prefs.getString("call_block_contacts", "[]");
                var blockList = Arrays.stream(callBlockList.substring(1, callBlockList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var blockNumber : blockList) {
                    if (!TextUtils.isEmpty(blockNumber) && Objects.equals(rawPhoneNumber, blockNumber)) {
                        return true;
                    }
                }
                return false;
            case WHITELIST:
                var callWhiteList = prefs.getString("call_white_contacts", "[]");
                var whiteList = Arrays.stream(callWhiteList.substring(1, callWhiteList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var whiteNumber : whiteList) {
                    if (!TextUtils.isEmpty(whiteNumber) && Objects.equals(rawPhoneNumber, whiteNumber)) {
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
