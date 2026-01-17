package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XSharedPreferences;


public class HideSeenView extends Feature {

    public HideSeenView(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public static void updateAllBubbleViews() {
        var adapter = ConversationItemListener.getAdapter();
        if (adapter instanceof CursorAdapter cursorAdapter) {
            WppCore.getCurrentActivity().runOnUiThread(cursorAdapter::notifyDataSetChanged);
        }
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_seen_view", false)) return;

        // Register listener
        ConversationItemListener.conversationListeners.add(new ConversationItemListener.OnConversationItemListener() {
            @Override
            public void onItemBind(FMessageWpp fMessage, ViewGroup viewGroup) {
                if (fMessage.getKey().isFromMe) return;
                updateBubbleView(fMessage, viewGroup);
            }
        });
    }

    @SuppressLint("ResourceType")
    private static void updateBubbleView(FMessageWpp fmessage, View viewGroup) {
        var userJid = fmessage.getKey().remoteJid;
        var messageId = fmessage.getKey().messageID;
        if (userJid.isNull()) return;
        ImageView view = viewGroup.findViewById(Utils.getID("view_once_control_icon", "id"));
        if (view != null) {
            var messageOnce = MessageHistory.getInstance().getHideSeenMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.VIEW_ONCE_TYPE);
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
            var message = MessageHistory.getInstance().getHideSeenMessage(userJid.getPhoneRawString(), messageId, MessageHistory.MessageType.MESSAGE_TYPE);
            if (message != null) {
                status.setVisibility(View.VISIBLE);
                status.setText(message.viewed ? "\uD83D\uDFE2" : "\uD83D\uDD34");
            } else {
                status.setVisibility(View.GONE);
            }
        }
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen View";
    }
}
