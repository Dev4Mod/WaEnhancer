package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideReceipt extends Feature {
    public HideReceipt(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        var hideReceipt = prefs.getBoolean("hidereceipt", false);
        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        var hideread = prefs.getBoolean("hideread", false);

        var method = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug("hook method:" + Unobfuscator.getMethodDescriptor(method));
        var method2 = Unobfuscator.loadReceiptOutsideChat(classLoader);
        logDebug("Outside Chat: " + Unobfuscator.getMethodDescriptor(method2));
        var mInChat = Unobfuscator.loadReceiptInChat(classLoader);
        logDebug("In Chat: " + Unobfuscator.getMethodDescriptor(mInChat));

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!ReflectionUtils.isCalledFromMethod(method2) && !ReflectionUtils.isCalledFromMethod(mInChat))
                    return;
                var key = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
                var messageKey = new FMessageWpp.Key(key);
                var userJid = messageKey.remoteJid;
                var rawJid = WppCore.getRawString(userJid);
                var number = WppCore.stripJID(rawJid);
                var privacy = CustomPrivacy.getJSON(number);
                var customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt);
                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                if (param.args[4] != "sender" && (customHideReceipt || ghostmode)) {
                    if (!ReflectionUtils.isCalledFromMethod(method2) && ReflectionUtils.isCalledFromMethod(mInChat) && !customHideRead) {
                        return;
                    }
                    param.args[4] = "inactive";
                }
                logDebug(param.args[4]);
                if (param.args[4] == "inactive") {
                    Object fmessageObj = WppCore.getFMessageFromKey(key);
                    var fmessage = new FMessageWpp(fmessageObj);
                    var messageId = fmessage.getKey().messageID;
                    MessageHistory.getInstance().insertHideSeenMessage(rawJid, messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                    if (fmessage.isViewOnce()) {
                        MessageHistory.getInstance().insertHideSeenMessage(rawJid, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, false);
                    }
                    HideSeenView.updateAllBubbleViews();
                }
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Receipt";
    }
}
