package com.wmods.wppenhacer.xposed.features.privacy;

import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.features.general.Tasker;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class CallPrivacy extends Feature {
    public CallPrivacy(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    /**
     * @noinspection unchecked
     */
    @Override
    public void doHook() throws Throwable {

        var onCallReceivedMethod = Unobfuscator.loadAntiRevokeOnCallReceivedMethod(classLoader);

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo = ((Message) param.args[0]).obj;
                Class<?> callInfoClass = XposedHelpers.findClass("com.whatsapp.voipcalling.CallInfo", classLoader);
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return;
                if ((boolean) XposedHelpers.callMethod(callinfo, "isCaller")) return;
                var userJid = XposedHelpers.callMethod(callinfo, "getPeerJid");
                var callId = XposedHelpers.callMethod(callinfo, "getCallId");
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                Tasker.sendTaskerEvent(WppCore.stripJID(WppCore.getRawString(userJid)), "call_received");
                var block = false;
                switch (type) {
                    case 0:
                        break;
                    case 1:
                        block = true;
                        break;
                    case 2:
                    case 3:
                    case 4:
                        block = checkCallBlock(userJid, type);
                        break;
                }
                if (!block) return;
                var clazzVoip = XposedHelpers.findClass("com.whatsapp.voipcalling.Voip", classLoader);
                var rejectType = prefs.getString("call_type", "no_internet");
                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                        XposedHelpers.callStaticMethod(clazzVoip, "rejectCall", callId, rejectType.equals("declined") ? null : rejectType);
                        param.setResult(true);
                        break;
                    case "ended":
                        try {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true);
                        } catch (NoSuchMethodError e) {
                            XposedHelpers.callStaticMethod(clazzVoip, "endCall", true, 0);
                        }
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
                log("Call received: " + param.args[0]);
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                var block = false;
                switch (type) {
                    case 0:
                        break;
                    case 1:
                        block = true;
                        break;
                    case 2:
                    case 3:
                    case 4:
                        block = checkCallBlock(userJid, type);
                        break;
                }
                if (!block) return;
                param.setResult(1);
            }
        });


    }

    public boolean checkCallBlock(Object userJid, int type) throws IllegalAccessException, InvocationTargetException {
        var jid = WppCore.stripJID(WppCore.getRawString(userJid));
        log("JID: " + jid);
        if (jid == null) return false;
        switch (type) {
            case 3:
                log("Call block list: " + prefs.getString("call_block_contacts", "[]"));
                var callBlockList = prefs.getString("call_block_contacts", "[]");
                var blockList = Arrays.stream(callBlockList.substring(1, callBlockList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var blockNumber : blockList) {
                    if (!TextUtils.isEmpty(blockNumber) && jid.contains(blockNumber)) {
                        log("Blocked number: " + blockNumber);
                        return true;
                    }
                }
                return false;
            case 4:
                var callWhiteList = prefs.getString("call_white_contacts", "[]");
                var whiteList = Arrays.stream(callWhiteList.substring(1, callWhiteList.length() - 1).split(", ")).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
                for (var whiteNumber : whiteList) {
                    if (!TextUtils.isEmpty(whiteNumber) && jid.contains(whiteNumber)) {
                        return false;
                    }
                }
                return true;
            default:
                if (WppCore.stripJID(jid).equals(jid)) {
                    jid = jid.split("\\.")[0] + "@s.whatsapp.net";
                }
                var contactName = WppCore.getContactName(WppCore.createUserJid(jid));
                return contactName == null || contactName.equals(jid);
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Privacy";
    }
}
