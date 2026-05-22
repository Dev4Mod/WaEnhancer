package com.wmods.wppenhacer.xposed.features.listeners;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ConversationItemListener extends Feature {

    public static final HashSet<OnConversationItemListener> conversationListeners = new HashSet<>();
    private static ListAdapter mAdapter;
    private static XC_MethodHook.Unhook hooked;

    public ConversationItemListener(@NonNull ClassLoader loader, @NonNull XSharedPreferences preferences) {
        super(loader, preferences);
    }

    public static ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void doHook() throws Throwable {
        XposedHelpers.findAndHookMethod(ListView.class, "setAdapter", ListAdapter.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity currentActivity = WppCore.getCurrentActivity();
                if (currentActivity == null || !currentActivity.getClass().getSimpleName().equals("Conversation")) {
                    return;
                }

                ListView listView = (ListView) param.thisObject;
                if (listView.getId() != android.R.id.list) {
                    return;
                }

                ListAdapter adapter = (ListAdapter) param.args[0];
                if (adapter instanceof HeaderViewListAdapter) {
                    adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
                }

                if (adapter == null) {
                    return;
                }

                mAdapter = adapter;

                for (OnConversationItemListener listener : conversationListeners) {
                    listener.onAttachAdapter(mAdapter);
                }

                if (hooked != null) {
                    hooked.unhook();
                }

                var method = mAdapter.getClass().getDeclaredMethod("getView", int.class, View.class, ViewGroup.class);
                hooked = XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject != mAdapter) return;

                        var position = (int) param.args[0];
                        var convertView = (View) param.args[1];
                        var viewGroup = (ViewGroup) param.getResult();

                        if (viewGroup == null) return;

                        Object fMessageObj = mAdapter.getItem(position);
                        if (fMessageObj == null) return;

                        var fMessage = new FMessageWpp(fMessageObj);

                        for (OnConversationItemListener listener : conversationListeners) {
                            try {
                                listener.onItemBind(fMessage, viewGroup, position, convertView);
                            } catch (Throwable e) {
                                logDebug(e);
                            }
                        }
                    }
                });
            }
        });
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Conversation Item Listener";
    }

    public abstract static class OnConversationItemListener {
        /**
         * Called when a message item is rendered in the conversation
         *
         * @param fMessage The message
         * @param view     The view associated with the item
         * @param position The position
         * @param convertView The view from the adapter
         * @throws Throwable Errors caught in the hook
         */
        public abstract void onItemBind(FMessageWpp fMessage, ViewGroup view, int position, View convertView) throws Throwable;

        public void onAttachAdapter(ListAdapter adapter) {

        }
    }
}