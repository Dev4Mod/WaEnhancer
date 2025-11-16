package com.wmods.wppenhacer.xposed.features.privacy;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.utils.DebugUtils;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

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
        var sendJob = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "SendReadReceiptJob");
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
                var lid = (String) XposedHelpers.getObjectField(srj, "jid");
                var userJid = new FMessageWpp.UserJid(lid);
                if (userJid.isNull()) return;
                var privacy = CustomPrivacy.getJSON(userJid.getPhoneNumber());

                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                var isHide = false;

                if (userJid.isGroup()) {
                    if (privacy.optBoolean("HideSeen", hideread_group) || ghostmode) {
                        param.setResult(null);
                        isHide = true;
                    }
                } else if (userJid.isStatus()) {
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
                    for (String messageId : messageIds) {
                        var fmessage = new FMessageWpp.Key(messageId, userJid, false).getFMessage();
                        MessageHistory.getInstance().insertHideSeenMessage(userJid.getPhoneRawString(), messageId, fmessage.isViewOnce() ? MessageHistory.MessageType.VIEW_ONCE_TYPE : MessageHistory.MessageType.MESSAGE_TYPE, false);
                        HideSeenView.updateAllBubbleViews();
                    }
                }

            }
        });


        Method ReceiptMethod = Unobfuscator.loadReceiptMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(ReceiptMethod));

        XposedBridge.hookMethod(ReceiptMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var keyObject = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
                var fmessage = new FMessageWpp.Key(keyObject).getFMessage();
                var keyMessage = fmessage.getKey();
                if (WppCore.getCurrentConversation() != WppCore.getCurrentActivity()) return;
                DebugUtils.debugArgs(param.args);
                if (MessageHistory.getInstance().getHideSeenMessage(keyMessage.remoteJid.getUserRawString(), keyMessage.messageID, fmessage.isViewOnce() ? MessageHistory.MessageType.VIEW_ONCE_TYPE : MessageHistory.MessageType.MESSAGE_TYPE) != null) {
                    return;
                }
                var userJid = ReflectionUtils.getArg(param.args, classLoader.loadClass("com.whatsapp.jid.Jid"), 0);
                if (userJid == null) return;
                var msgTypeIdx = ReflectionUtils.findIndexOfType(((Method) param.method).getParameterTypes(), String.class);
                if (!Objects.equals("read", param.args[msgTypeIdx])) return;
                var privacy = CustomPrivacy.getJSON(keyMessage.remoteJid.getPhoneNumber());
                var customHideRead = privacy.optBoolean("HideSeen", hideread);
                if (keyMessage.remoteJid.isGroup()) {
                    if (privacy.optBoolean("HideSeen", hideread_group) || ghostmode) {
                        param.args[msgTypeIdx] = null;
                    }
                } else if (customHideRead || ghostmode) {
                    param.args[msgTypeIdx] = null;
                }

                if (param.args[msgTypeIdx] == null) {
                    MessageHistory.getInstance().insertHideSeenMessage(keyMessage.remoteJid.getPhoneRawString(), keyMessage.messageID, fmessage.isViewOnce() ? MessageHistory.MessageType.VIEW_ONCE_TYPE : MessageHistory.MessageType.MESSAGE_TYPE, false);
                    HideSeenView.updateAllBubbleViews();
                }
            }
        });

        var loadSenderPlayed = Unobfuscator.loadSenderPlayedMethod(classLoader);
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
                var userJid = key.remoteJid;
                var messageId = key.messageID;
                if (isHide) {
                    MessageHistory.getInstance().insertHideSeenMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                }
                if (fMessage.isViewOnce() && !hideonceseen && !ghostmode) {
                    MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                    MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
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
                    var userJid = key.remoteJid;
                    var messageId = key.messageID;
                    if (isHide) {
                        MessageHistory.getInstance().insertHideSeenMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.MESSAGE_TYPE, false);
                    }
                    if (fMessage.isViewOnce() && !hideonceseen && !ghostmode) {
                        MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
                        MessageHistory.getInstance().updateViewedMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
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
