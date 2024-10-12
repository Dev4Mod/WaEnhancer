package com.wmods.wppenhacer.xposed.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;
import com.wmods.wppenhacer.xposed.utils.ResId;
import com.wmods.wppenhacer.xposed.utils.Utils;

public class WaCallback implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        WppCore.mCurrentActivity = activity;
        WppCore.activities.add(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        WppCore.mCurrentActivity = activity;
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.START);
        WppCore.activities.add(activity);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        WppCore.mCurrentActivity = activity;
        WppCore.activities.add(activity);
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
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.RESUME);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.PAUSE);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.END);
        WppCore.activities.remove(activity);
    }

    private static void triggerActivityState(@NonNull Activity activity, WppCore.ActivityChangeState.ChangeType type) {
        for (WppCore.ActivityChangeState listener : WppCore.listenerAcitivity) {
            listener.onChange(activity, type);
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        WppCore.activities.remove(activity);
    }
}
