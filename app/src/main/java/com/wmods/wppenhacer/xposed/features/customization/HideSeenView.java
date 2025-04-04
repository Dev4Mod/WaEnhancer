package com.wmods.wppenhacer.xposed.features.customization;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.MessageHistory;
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils;
import com.wmods.wppenhacer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;


public class HideSeenView extends Feature {
    private static ListAdapter mAdapter;

    public HideSeenView(ClassLoader loader, XSharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        if (!prefs.getBoolean("hide_seen_view", false)) return;

        XposedHelpers.findAndHookMethod(ListView.class, "setAdapter", ListAdapter.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!WppCore.getCurrentActivity().getClass().getSimpleName().equals("Conversation"))
                    return;
                if (((ListView)param.thisObject).getId() != android.R.id.list) return;
                ListAdapter adapter = (ListAdapter) param.args[0];
                if (adapter instanceof HeaderViewListAdapter) {
                    adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
                }
                if (adapter == null) return;
                mAdapter = adapter;
                var method = mAdapter.getClass().getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mAdapter) return;
                        var position = (int) param.args[0];
                        var viewGroup = (ViewGroup) param.args[1];
                        if (viewGroup == null) return;
                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;
                        var fmessage = new FMessageWpp(fMessageObj);
                        if (fmessage.getKey().isFromMe) return;
                        viewGroup.post(() -> updateBubbleView(fmessage, viewGroup));
                    }
                });
            }
        });
    }

    public static void updateAllBubbleViews() {
        if (mAdapter instanceof CursorAdapter cursorAdapter) {
            WppCore.getCurrentActivity().runOnUiThread(cursorAdapter::notifyDataSetChanged);
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
    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Hide Seen View";
    }
}
