package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;

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

        logDebug("hideReceipt: " + hideReceipt + ", ghostmode: " + ghostmode + ", hideread: " + hideread);

        var method = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug("hook method:" + Unobfuscator.getMethodDescriptor(method));

        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var userJid = ReflectionUtils.getArg(param.args, Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid"), 0);
                var currentUserJid = new FMessageWpp.UserJid(userJid);
                var key = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
                var fmessage = new FMessageWpp.Key(key).getFMessage();
                if (fmessage != null) {
                    currentUserJid = fmessage.getKey().remoteJid;
                    if (MessageHistory.getInstance().getHideSeenMessage(fmessage.getKey().remoteJid.getPhoneRawString(), fmessage.getKey().messageID, fmessage.isViewOnce() ? MessageHistory.MessageType.VIEW_ONCE_TYPE : MessageHistory.MessageType.MESSAGE_TYPE) != null) {
                        return;
                    }
                }
                var privacy = CustomPrivacy.getJSON(currentUserJid.getPhoneNumber());
                var customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt);
                var msgTypeIdx = ReflectionUtils.findIndexOfType(((Method) param.method).getParameterTypes(), String.class);
                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                if (param.args[msgTypeIdx] != "sender" && (customHideReceipt || ghostmode)) {
                    if (WppCore.getCurrentConversation() == null || customHideRead)
                        param.args[msgTypeIdx] = "inactive";
                }
                if (param.args[msgTypeIdx] == "inactive") {
                    MessageHistory.getInstance().insertHideSeenMessage(currentUserJid.getPhoneRawString(), fmessage.getKey().messageID, fmessage.isViewOnce() ? MessageHistory.MessageType.VIEW_ONCE_TYPE : MessageHistory.MessageType.MESSAGE_TYPE, false);
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
