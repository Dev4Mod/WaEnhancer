package com.wmods.wppenhacer.xposed.features.privacy;

import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;

import org.json.JSONObject;
import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HideSeen extends Feature {

    private static final int MEDIA_TYPE_VOICE_NOTE = 2;

    private boolean ghostMode;
    private boolean hideRead;
    private boolean hideAudioSeen;
    private boolean hideOnceSeen;
    private boolean hideReadGroup;
    private boolean hideStatusView;

    public HideSeen(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    protected static FMessageWpp.Key getKeyMessage(XC_MethodHook.MethodHookParam param, Object userJidObject,
                                                   List<Pair<Integer, Class<? extends String>>> strings) {
        Object keyObject = ReflectionUtils.getArg(param.args, FMessageWpp.Key.TYPE, 0);
        if (keyObject == null) {
            if (strings.size() < 2) return null;
            String idMessage = (String) param.args[strings.get(0).first];
            FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(userJidObject);
            return new FMessageWpp.Key(idMessage, userJid, false);
        }
        return new FMessageWpp.Key(keyObject);
    }

    @Override
    public void doHook() throws Exception {
        loadPreferences();
        hookSendReadReceiptJob();
        hookReceiptMethod();
        hookSenderPlayed();
        hookSenderPlayedBusiness();
    }

    private void loadPreferences() {
        ghostMode = WppCore.getPrivBoolean("ghostmode", false);
        hideRead = prefs.getBoolean("hideread", false);
        hideAudioSeen = prefs.getBoolean("hideaudioseen", false);
        hideOnceSeen = prefs.getBoolean("hideonceseen", false);
        hideReadGroup = prefs.getBoolean("hideread_group", false);
        hideStatusView = prefs.getBoolean("hidestatusview", false);
    }

    private void hookSendReadReceiptJob() throws Exception {
        Method sendReadReceiptJobMethod = Unobfuscator.loadHideViewSendReadJob(classLoader);
        Class<?> sendJobClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "SendReadReceiptJob");
        log(Unobfuscator.getMethodDescriptor(sendReadReceiptJobMethod));

        XposedBridge.hookMethod(sendReadReceiptJobMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!sendJobClass.isInstance(param.thisObject)) return;

                Object sendReadReceiptJob = sendJobClass.cast(param.thisObject);
                if (hasBlueOnReplyFlag(sendReadReceiptJob)) return;

                String lid = (String) XposedHelpers.getObjectField(sendReadReceiptJob, "jid");
                if (isInvalidJid(lid)) return;

                FMessageWpp.UserJid userJid = new FMessageWpp.UserJid(lid);
                if (userJid.isNull()) return;

                JSONObject privacy = CustomPrivacy.getJSON(userJid.getPhoneNumber());
                boolean isHide = processReadReceiptByType(param, sendReadReceiptJob, userJid, privacy);

                if (isHide) {
                    recordHiddenMessages(sendReadReceiptJob, userJid);
                }
            }
        });
    }

    private boolean hasBlueOnReplyFlag(Object sendReadReceiptJob) {
        return XposedHelpers.getAdditionalInstanceField(sendReadReceiptJob, "blue_on_reply") != null;
    }

    private boolean isInvalidJid(String lid) {
        return TextUtils.isEmpty(lid) || lid.contains("lid_me") || lid.contains("status_me");
    }

    private boolean processReadReceiptByType(XC_MethodHook.MethodHookParam param, Object job,
                                             FMessageWpp.UserJid userJid, JSONObject privacy) {
        if (userJid.isGroup()) {
            return processGroupReadReceipt(param, privacy);
        }
        if (userJid.isStatus()) {
            processStatusReadReceipt(param, job);
            return false;
        }
        return processDirectReadReceipt(param, privacy);
    }

    private boolean processGroupReadReceipt(XC_MethodHook.MethodHookParam param, JSONObject privacy) {
        if (privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode) {
            param.setResult(null);
            return true;
        }
        return false;
    }

    private void processStatusReadReceipt(XC_MethodHook.MethodHookParam param, Object job) {
        String participant = (String) XposedHelpers.getObjectField(job, "participant");
        boolean customHideStatusView = CustomPrivacy.getJSON(WppCore.stripJID(participant))
                .optBoolean("HideViewStatus", hideStatusView);

        if (customHideStatusView || ghostMode) {
            param.setResult(null);
        }
    }

    private boolean processDirectReadReceipt(XC_MethodHook.MethodHookParam param, JSONObject privacy) {
        boolean customHideRead = privacy.optBoolean("HideSeen", hideRead);
        if (customHideRead || ghostMode) {
            param.setResult(null);
            return true;
        }
        return false;
    }

    private void recordHiddenMessages(Object sendReadReceiptJob, FMessageWpp.UserJid userJid) {
        String[] messageIds = (String[]) XposedHelpers.getObjectField(sendReadReceiptJob, "messageIds");
        for (String messageId : messageIds) {
            FMessageWpp fMessage = new FMessageWpp.Key(messageId, userJid, false).getFMessage();
            MessageHistory.MessageType type = fMessage.isViewOnce()
                    ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                    : MessageHistory.MessageType.MESSAGE_TYPE;
            MessageHistory.getInstance().insertHideSeenMessage(userJid.getPhoneRawString(), messageId, type, false);
        }
        HideSeenView.updateAllBubbleViews();
    }

    private void hookReceiptMethod() throws Exception {
        Method receiptMethod = Unobfuscator.loadReceiptMethod(classLoader);
        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(classLoader);
        Method outsideMethod = Unobfuscator.loadReceiptOutsideChat(classLoader);

        logDebug("ReceiptMethod", Unobfuscator.getMethodDescriptor(receiptMethod));
        logDebug("Inside Chat", Unobfuscator.getMethodDescriptor(hideViewInChatMethod));
        logDebug("Outside Chat", Unobfuscator.getMethodDescriptor(outsideMethod));

        XposedBridge.hookMethod(receiptMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!isValidChatContext(outsideMethod, hideViewInChatMethod)) return;

                Class<?> jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
                Object userJidObject = ReflectionUtils.getArg(param.args, jidClass, 0);
                if (userJidObject == null) return;

                List<Pair<Integer, Class<? extends String>>> strings = ReflectionUtils.findClassesOfType(
                        ((Method) param.method).getParameterTypes(), String.class);
                FMessageWpp.Key keyMessage = getKeyMessage(param, userJidObject, strings);
                if (keyMessage == null) return;

                FMessageWpp fMessage = keyMessage.getFMessage();
                if (isAlreadyHidden(keyMessage, fMessage)) return;

                int msgTypeIdx = strings.get(strings.size() - 1).first;
                if (!Objects.equals("read", param.args[msgTypeIdx])) return;

                processReceiptHiding(param, keyMessage, fMessage, msgTypeIdx);
            }
        });
    }

    private boolean isValidChatContext(Method outsideMethod, Method hideViewInChatMethod) {
        if (WppCore.getCurrentConversation() != WppCore.getCurrentActivity()) return false;
        return !ReflectionUtils.isCalledFromMethod(outsideMethod) && ReflectionUtils.isCalledFromMethod(hideViewInChatMethod);
    }

    private boolean isAlreadyHidden(FMessageWpp.Key keyMessage, FMessageWpp fMessage) {
        if (fMessage == null) return false;
        MessageHistory.MessageType type = fMessage.isViewOnce()
                ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                : MessageHistory.MessageType.MESSAGE_TYPE;
        return MessageHistory.getInstance().getHideSeenMessage(
                keyMessage.remoteJid.getPhoneRawString(), keyMessage.messageID, type) != null;
    }

    private void processReceiptHiding(XC_MethodHook.MethodHookParam param, FMessageWpp.Key keyMessage,
                                      FMessageWpp fMessage, int msgTypeIdx) {
        JSONObject privacy = CustomPrivacy.getJSON(keyMessage.remoteJid.getPhoneNumber());
        boolean shouldHide = shouldHideReceipt(keyMessage.remoteJid, privacy);

        if (shouldHide) {
            param.args[msgTypeIdx] = null;
        }

        if (param.args[msgTypeIdx] == null && fMessage != null) {
            MessageHistory.MessageType type = fMessage.isViewOnce()
                    ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                    : MessageHistory.MessageType.MESSAGE_TYPE;
            MessageHistory.getInstance().insertHideSeenMessage(
                    keyMessage.remoteJid.getPhoneRawString(), keyMessage.messageID, type, false);
            HideSeenView.updateAllBubbleViews();
        }
    }

    private boolean shouldHideReceipt(FMessageWpp.UserJid userJid, JSONObject privacy) {
        if (userJid.isGroup()) {
            return privacy.optBoolean("HideSeen", hideReadGroup) || ghostMode;
        }
        return privacy.optBoolean("HideSeen", hideRead) || ghostMode;
    }

    private void hookSenderPlayed() throws Exception {
        Method loadSenderPlayed = Unobfuscator.loadSenderPlayedMethod(classLoader);

        XposedBridge.hookMethod(loadSenderPlayed, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                FMessageWpp fMessage = new FMessageWpp(param.args[0]);
                processSenderPlayed(param, fMessage);
            }
        });
    }

    private void hookSenderPlayedBusiness() throws Exception {
        Method loadSenderPlayedBusiness = Unobfuscator.loadSenderPlayedBusiness(classLoader);

        XposedBridge.hookMethod(loadSenderPlayedBusiness, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Set<?> set = (Set<?>) param.args[0];
                if (set == null || set.isEmpty()) return;

                FMessageWpp fMessage = new FMessageWpp(set.iterator().next());
                processSenderPlayed(param, fMessage);
            }
        });
    }

    private void processSenderPlayed(XC_MethodHook.MethodHookParam param, FMessageWpp fMessage) {
        int mediaType = fMessage.getMediaType();
        boolean isHide = false;

        if (shouldHideViewOnce(fMessage)) {
            param.setResult(null);
            isHide = true;
        } else if (shouldHideVoiceNote(mediaType)) {
            param.setResult(null);
            isHide = true;
        }

        FMessageWpp.Key key = fMessage.getKey();
        if (isHide) {
            MessageHistory.getInstance().insertHideSeenMessage(
                    key.remoteJid.getPhoneRawString(), key.messageID, MessageHistory.MessageType.MESSAGE_TYPE, false);
        }

        handleViewOnceViewed(fMessage, key);
        HideSeenView.updateAllBubbleViews();
    }

    private boolean shouldHideViewOnce(FMessageWpp fMessage) {
        return (hideOnceSeen || ghostMode) && fMessage.isViewOnce();
    }

    private boolean shouldHideVoiceNote(int mediaType) {
        return (hideAudioSeen || ghostMode) && mediaType == MEDIA_TYPE_VOICE_NOTE;
    }

    private void handleViewOnceViewed(FMessageWpp fMessage, FMessageWpp.Key key) {
        if (fMessage.isViewOnce() && !hideOnceSeen && !ghostMode) {
            String phoneRaw = key.remoteJid.getPhoneRawString();
            String messageId = key.messageID;
            MessageHistory.getInstance().updateViewedMessage(phoneRaw, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE, true);
            MessageHistory.getInstance().updateViewedMessage(phoneRaw, messageId, MessageHistory.MessageType.MESSAGE_TYPE, true);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen";
    }
}
