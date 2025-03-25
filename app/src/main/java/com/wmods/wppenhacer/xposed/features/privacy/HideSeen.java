package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideSeen extends Feature {

    public HideSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {


        Method SendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader);
        var sendJob = XposedHelpers.findClass("com.whatsapp.jobqueue.job.SendReadReceiptJob", classLoader);
        log(Unobfuscator.getMethodDescriptor(SendReadReceiptJobMethod));

        var ghostmode = WppCore.getPrivBoolean("ghostmode", false);
        var hideread = prefs.getBoolean("hideread", false);
        var hideaudioseen = prefs.getBoolean("hideaudioseen", false);
        var hideonceseen = prefs.getBoolean("hideonceseen", false);
        var hideread_group = prefs.getBoolean("hideread_group", false);
        var hidestatusview = prefs.getBoolean("hidestatusview", false);

        XposedBridge.hookMethod(SendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!sendJob.isInstance(param.thisObject)) return;
                var srj = sendJob.cast(param.thisObject);
                var messageIds = (String[]) XposedHelpers.getObjectField(srj, "messageIds");
                var firstmessage = messageIds[0];
                if (firstmessage != null && WppCore.getPrivBoolean(firstmessage + "_rpass", false)) {
                    WppCore.removePrivKey(firstmessage + "_rpass");
                    return;
                }
                var jid = (String) XposedHelpers.getObjectField(srj, "jid");
                if (jid == null) return;
                var number = WppCore.stripJID(jid);
                var privacy = CustomPrivacy.getJSON(number);

                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                var isHide = false;

                if (WppCore.isGroup(jid)) {
                    if (privacy.optBoolean("HideSeen", hideread_group) || ghostmode) {
                        param.setResult(null);
                        isHide = true;
                    }
                } else if (jid.startsWith("status")) {
                    var participant = (String) XposedHelpers.getObjectField(srj, "participant");
                    var customHideStatusView = CustomPrivacy.getJSON(WppCore.stripJID(participant)).optBoolean("HideViewStatus", hidestatusview);
                    if (customHideStatusView || ghostmode) {
                        param.setResult(null);
                    }
                } else if (customHideRead || ghostmode) {
                    param.setResult(null);
                    isHide = true;
                }
                if (isHide) {
                    var keyClass = FMessageWpp.Key.TYPE;
                    for (String messageId : messageIds) {
                        MessageHistory.getInstance().insertHideSeenMessage(jid, messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                        var userJid = WppCore.createUserJid(jid);
                        var key = keyClass.getConstructors()[0].newInstance(userJid, messageId, false);
                        var fmessage = new FMessageWpp(WppCore.getFMessageFromKey(key));
                        if (fmessage.isViewOnce()) {
                            MessageHistory.getInstance().insertHideSeenMessage(jid, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, false);
                        }
                        HideSeenView.updateAllBubbleViews();
                    }
                }

            }
        });

        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(classLoader);
        logDebug("Inside Chat", Unobfuscator.getMethodDescriptor(hideViewInChatMethod));

        Method ReceiptMethod = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(ReceiptMethod));

        var method3 = Unobfuscator.loadReceiptOutsideChat(classLoader);
        logDebug("Outside Chat", Unobfuscator.getMethodDescriptor(method3));


        XposedBridge.hookMethod(ReceiptMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (ReflectionUtils.isCalledFromMethod(method3) || !ReflectionUtils.isCalledFromMethod(hideViewInChatMethod))
                    return;
                if (!Objects.equals("read", param.args[4])) return;
                var jid = WppCore.getCurrentRawJID();
                var number = WppCore.stripJID(jid);
                var privacy = CustomPrivacy.getJSON(number);
                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                if (WppCore.isGroup(jid)) {
                    if (privacy.optBoolean("HideSeen", hideread_group) || ghostmode) {
                        param.args[4] = null;
                    }
                } else if (customHideRead || ghostmode) {
                    param.args[4] = null;
                }

                if (param.args[4] == null) {
                    var key = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
                    if (key != null) {
                        var fmessage = new FMessageWpp(WppCore.getFMessageFromKey(key));
                        var messageId = fmessage.getKey().messageID;
                        MessageHistory.getInstance().insertHideSeenMessage(jid, messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                        if (fmessage.isViewOnce()) {
                            MessageHistory.getInstance().insertHideSeenMessage(jid, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, false);
                        }
                        HideSeenView.updateAllBubbleViews();
                    }
                }
            }
        });

        var loadSenderPlayed = Unobfuscator.loadSenderPlayed(classLoader);
        XposedBridge.hookMethod(loadSenderPlayed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var fMessage = new FMessageWpp(param.args[0]);
                var media_type = fMessage.getMediaType();  // 2 = voice note ; 82 = viewonce note voice; 42 = image view once; 43 = video view once
                var isHide = false;
                if ((hideonceseen || ghostmode) && fMessage.isViewOnce()) {
                    param.setResult(null);
                } else if ((hideaudioseen || ghostmode) && media_type == 2) {
                    param.setResult(null);
                    isHide = true;
                }
                var key = fMessage.getKey();
                var jid = key.remoteJid;
                var messageId = key.messageID;
                if (isHide) {
                    MessageHistory.getInstance().insertHideSeenMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                }
                if (fMessage.isViewOnce() && !hideonceseen && !ghostmode) {
                    MessageHistory.getInstance().updateViewedMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                    MessageHistory.getInstance().updateViewedMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
                }
                HideSeenView.updateAllBubbleViews();
            }
        });

        var loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader);
        XposedBridge.hookMethod(loadSenderPlayedBusiness, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var set = (Set) param.args[0];
                if (set != null && !set.isEmpty()) {
                    var fMessage = new FMessageWpp(set.iterator().next());
                    var media_type = fMessage.getMediaType();  // 2 = voice note ; 82 = viewonce note voice; 42 = image view once; 43 = video view once
                    var isHide = false;
                    if ((hideonceseen || ghostmode) && fMessage.isViewOnce()) {
                        param.setResult(null);
                        isHide = true;
                    } else if ((hideaudioseen || ghostmode) && media_type == 2) {
                        param.setResult(null);
                        isHide = true;
                    }
                    var key = fMessage.getKey();
                    var jid = key.remoteJid;
                    var messageId = key.messageID;
                    if (isHide) {
                        MessageHistory.getInstance().insertHideSeenMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                    }
                    if (fMessage.isViewOnce() && !hideonceseen && !ghostmode) {
                        MessageHistory.getInstance().updateViewedMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                        MessageHistory.getInstance().updateViewedMessage(WppCore.getRawString(jid), messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
                    }
                    HideSeenView.updateAllBubbleViews();
                }
            }
        });


    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }

}
