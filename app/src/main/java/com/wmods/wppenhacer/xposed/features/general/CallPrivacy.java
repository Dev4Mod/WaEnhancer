package com.wmods.wppenhacer.xposed.features.general;

import android.os.Message;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.Unobfuscator;
import com.wmods.wppenhacer.xposed.core.WppCore;

import java.lang.reflect.InvocationTargetException;

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
//        var callEndMethod = Unobfuscator.loadAntiRevokeCallEndMethod(loader);
//        var callState = Enum.valueOf((Class<Enum>) XposedHelpers.findClass("com.whatsapp.voipcalling.CallState", loader), "ACTIVE");

        XposedBridge.hookMethod(onCallReceivedMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object callinfo = ((Message) param.args[0]).obj;
                Class<?> callInfoClass = XposedHelpers.findClass("com.whatsapp.voipcalling.CallInfo", classLoader);
                if (callinfo == null || !callInfoClass.isInstance(callinfo)) return;
                if ((boolean) XposedHelpers.callMethod(callinfo, "isCaller")) return;
                var callId = XposedHelpers.callMethod(callinfo, "getCallId");
                var type = Integer.parseInt(prefs.getString("call_privacy", "0"));
                var block = false;
                switch (type) {
                    case 0:
                        break;
                    case 1:
                        block = true;
                        break;
                    case 2:
                        block = checkCallBlock(callinfo);
                        break;
                }
                if (!block) return;
//                XposedHelpers.callMethod(param.thisObject, callEndMethod.getName(), callState, callinfo);
                var clazzVoip = XposedHelpers.findClass("com.whatsapp.voipcalling.Voip", classLoader);
                var rejectType = prefs.getString("call_type", "ended");
                switch (rejectType) {
                    case "uncallable":
                    case "declined":
                        XposedHelpers.callStaticMethod(clazzVoip, "rejectCall", callId, rejectType.equals("declined") ? null : rejectType);
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
                param.setResult(false);
            }
        });


    }

    public boolean checkCallBlock(Object callinfo) throws IllegalAccessException, InvocationTargetException {
        var userJid = XposedHelpers.callMethod(callinfo, "getPeerJid");
        var jid = WppCore.stripJID(WppCore.getRawString(userJid));
        var contactName = WppCore.getContactName(userJid);
        return contactName == null || contactName.equals(jid);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Call Privacy";
    }
}
