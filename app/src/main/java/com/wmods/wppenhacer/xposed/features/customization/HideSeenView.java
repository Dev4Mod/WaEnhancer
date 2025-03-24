package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.HashSet;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;


public class HideSeenView extends Feature {

    private static final HashSet<ViewGroup> bubbles = new HashSet<>();

    public HideSeenView(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_seen_view",false))return;

        var bubbleMethod = Unobfuscator.loadAntiRevokeBubbleMethod(classLoader);
        logDebug(Unobfuscator.getMethodDescriptor(bubbleMethod));

        WppCore.addListenerActivity(((activity, type) -> {
            if (type == WppCore.ActivityChangeState.ChangeType.ENDED && Objects.equals(activity.getClass().getSimpleName(), "Conversation")) {
                bubbles.clear();
            }
        }));

        XposedBridge.hookMethod(bubbleMethod, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var viewGroup = (ViewGroup) param.thisObject;
                var fmessageObj = ReflectionUtils.getArg(param.args, FMessageWpp.TYPE, 0);
                var fmessage = new FMessageWpp(fmessageObj);
                if (fmessage.getKey().isFromMe) return;
                updateBubbleView(fmessage, viewGroup);
                bubbles.add(viewGroup);
            }
        });
    }

    /**
     * Static method to update all bubble views in the HashMap
     * Call this method whenever you need to refresh all bubble views
     */
    public static void updateAllBubbleViews() {
        for (var viewGroup : bubbles) {
            try {
                Object messageObj = XposedHelpers.getAdditionalInstanceField(viewGroup, "FMessage");
                if (messageObj instanceof FMessageWpp fmessage) {
                    viewGroup.post(() -> updateBubbleView(fmessage, viewGroup));
                }
            } catch (Exception e) {
                XposedBridge.log("Error updating bubble view: " + e.getMessage());
            }
        }
    }

    @SuppressLint("ResourceType")
    private static void updateBubbleView(FMessageWpp fmessage, View viewGroup) {
        var jid = WppCore.getRawString(fmessage.getKey().remoteJid);
        var messageId = fmessage.getKey().messageID;
        ImageView view = viewGroup.findViewById(Utils.getID("view_once_control_icon", "id"));
        if (view != null) {
            var messageOnce = MessageHistory.getInstance().getHideSeenMessage(jid, messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE);
            if (messageOnce != null) {
                view.setColorFilter(messageOnce.viewed ? Color.GREEN : Color.RED);
            } else {
                view.setColorFilter(null);
            }
        }
        ViewGroup dateWrapper = viewGroup.findViewById(Utils.getID("date_wrapper", "id"));
        if (dateWrapper != null) {
            TextView status = dateWrapper.findViewById(0xf7ff2001);
            if (status == null) {
                status = new TextView(viewGroup.getContext());
                status.setId(0xf7ff2001);
                status.setTextSize(8);
                dateWrapper.addView(status);
            }
            var message = MessageHistory.getInstance().getHideSeenMessage(jid, messageId, MessageHistory.MessageType.MESSAGE_TYPE);
            if (message != null) {
                status.setVisibility(View.VISIBLE);
                status.setText(message.viewed ? "\uD83D\uDFE2" : "\uD83D\uDD34");
            } else {
                status.setVisibility(View.GONE);
            }
        }
        XposedHelpers.setAdditionalInstanceField(viewGroup, "FMessage", fmessage);
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen View";
    }
}
