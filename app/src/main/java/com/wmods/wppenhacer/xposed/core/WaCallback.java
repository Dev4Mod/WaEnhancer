package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;

import de.robv.android.xposed.XposedHelpers;

public class WaCallback implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        checkIsConversation(activity, WppCore.ObjectOnChangeListener.TYPE_START);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (WppCore.getPrivBoolean("need_restart", false)) {
            WppCore.setPrivBoolean("need_restart", false);
            try {
                new AlertDialogWpp(activity).
                        setMessage(activity.getString(ResId.string.restart_wpp)).
                        setPositiveButton(activity.getString(ResId.string.yes), (dialog, which) -> {
                            if (!Utils.doRestart(activity))
                                Toast.makeText(activity, "Unable to rebooting activity", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(activity.getString(ResId.string.no), null)
                        .show();
            } catch (Exception ignored) {
            }
        }
        checkIsConversation(activity, WppCore.ObjectOnChangeListener.TYPE_RESUME);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        checkIsConversation(activity, WppCore.ObjectOnChangeListener.TYPE_PAUSE);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        checkIsConversation(activity, WppCore.ObjectOnChangeListener.TYPE_END);
    }

    private static void checkIsConversation(@NonNull Activity activity, int type) {
        Class<?> conversation = XposedHelpers.findClass("com.whatsapp.Conversation", activity.getClassLoader());
        if (conversation.isInstance(activity)) {
            WppCore.mConversation = type == WppCore.ObjectOnChangeListener.TYPE_PAUSE || WppCore.ObjectOnChangeListener.TYPE_END == type ? null : activity;
            for (WppCore.ObjectOnChangeListener listener : WppCore.listenerChat) {
                listener.onChange(activity, type);
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }
}
