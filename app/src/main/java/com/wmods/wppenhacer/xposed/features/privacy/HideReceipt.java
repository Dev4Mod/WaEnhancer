package com.wmods.wppenhacer.xposed.features.privacy;

import static com.wmods.wppenhacer.xposed.features.privacy.HideSeen.getKeyMessage;

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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class HideReceipt extends Feature {

    private static final String RECEIPT_TYPE_SENDER = "sender";
    private static final String RECEIPT_TYPE_INACTIVE = "inactive";

    private boolean hideReceipt;
    private boolean ghostMode;
    private boolean hideRead;

    public HideReceipt(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {
        loadPreferences();
        logPreferences();
        hookReceiptMethod();
    }

    private void loadPreferences() {
        hideReceipt = prefs.getBoolean("hidereceipt", false);
        ghostMode = WppCore.getPrivBoolean("ghostmode", false);
        hideRead = prefs.getBoolean("hideread", false);
    }

    private void logPreferences() {
        logDebug("hideReceipt: " + hideReceipt + ", ghostmode: " + ghostMode + ", hideread: " + hideRead);
    }

    private void hookReceiptMethod() throws Exception {
        Method receiptMethod = Unobfuscator.loadReceiptMethod(classLoader);
        Method hideViewInChatMethod = Unobfuscator.loadHideViewInChatMethod(classLoader);
        Method outsideMethod = Unobfuscator.loadReceiptOutsideChat(classLoader);

        logDebug("hook method:" + Unobfuscator.getMethodDescriptor(receiptMethod));
        logDebug("Inside Chat", Unobfuscator.getMethodDescriptor(hideViewInChatMethod));
        logDebug("Outside Chat", Unobfuscator.getMethodDescriptor(outsideMethod));

        XposedBridge.hookMethod(receiptMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!isValidCallContext(outsideMethod, hideViewInChatMethod)) return;

                FMessageWpp.Key keyMessage = extractKeyMessage(param);
                if (keyMessage == null) return;

                FMessageWpp fMessage = keyMessage.getFMessage();
                if (isAlreadyHidden(fMessage)) return;

                Object userJidObject = extractUserJidObject(param);
                if (userJidObject == null) return;

                FMessageWpp.UserJid currentUserJid = new FMessageWpp.UserJid(userJidObject);
                processReceiptHiding(param, fMessage, currentUserJid);
            }
        });
    }

    private boolean isValidCallContext(Method outsideMethod, Method hideViewInChatMethod) {
        return ReflectionUtils.isCalledFromMethod(outsideMethod) || ReflectionUtils.isCalledFromMethod(hideViewInChatMethod);
    }

    private Object extractUserJidObject(XC_MethodHook.MethodHookParam param) throws Exception {
        Class<?> jidClass = Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.Jid");
        return ReflectionUtils.getArg(param.args, jidClass, 0);
    }

    private FMessageWpp.Key extractKeyMessage(XC_MethodHook.MethodHookParam param) throws Exception {
        Object userJidObject = extractUserJidObject(param);
        if (userJidObject == null) return null;

        List<Pair<Integer, Class<? extends String>>> strings = ReflectionUtils.findClassesOfType(
                ((Method) param.method).getParameterTypes(), String.class);
        return getKeyMessage(param, userJidObject, strings);
    }

    private boolean isAlreadyHidden(FMessageWpp fMessage) {
        if (fMessage == null) return false;

        FMessageWpp.Key key = fMessage.getKey();
        MessageHistory.MessageType type = fMessage.isViewOnce()
                ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                : MessageHistory.MessageType.MESSAGE_TYPE;

        return MessageHistory.getInstance().getHideSeenMessage(
                key.remoteJid.getPhoneRawString(), key.messageID, type) != null;
    }

    private void processReceiptHiding(XC_MethodHook.MethodHookParam param, FMessageWpp fMessage,
                                      FMessageWpp.UserJid currentUserJid) {
        JSONObject privacy = CustomPrivacy.getJSON(currentUserJid.getPhoneNumber());
        List<Pair<Integer, Class<? extends String>>> strings = ReflectionUtils.findClassesOfType(
                ((Method) param.method).getParameterTypes(), String.class);
        int msgTypeIdx = strings.get(strings.size() - 1).first;

        if (shouldHideReceipt(param, privacy, msgTypeIdx)) {
            param.args[msgTypeIdx] = RECEIPT_TYPE_INACTIVE;
        }

        if (RECEIPT_TYPE_INACTIVE.equals(param.args[msgTypeIdx])) {
            recordHiddenMessage(fMessage, currentUserJid);
        }
    }

    private boolean shouldHideReceipt(XC_MethodHook.MethodHookParam param, JSONObject privacy, int msgTypeIdx) {
        boolean customHideReceipt = privacy.optBoolean("HideReceipt", hideReceipt);
        boolean customHideRead = privacy.optBoolean("HideSeen", hideRead);

        if (RECEIPT_TYPE_SENDER.equals(param.args[msgTypeIdx])) {
            return false;
        }

        if (customHideReceipt || ghostMode) {
            return !WppCore.isConversationResumed() || customHideRead || ghostMode;
        }

        return false;
    }

    private void recordHiddenMessage(FMessageWpp fMessage, FMessageWpp.UserJid userJid) {
        FMessageWpp.Key key = fMessage.getKey();
        MessageHistory.MessageType type = fMessage.isViewOnce()
                ? MessageHistory.MessageType.VIEW_ONCE_TYPE
                : MessageHistory.MessageType.MESSAGE_TYPE;

        MessageHistory.getInstance().insertHideSeenMessage(
                userJid.getPhoneRawString(), key.messageID, type, false);
        HideSeenView.updateAllBubbleViews();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Receipt";
    }
}
