package com.wmods.wppenhacer.xposed.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wmods.wppenhacer.MainActivity;
import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp;

public class WaCallback implements Application.ActivityLifecycleCallbacks {
    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        var prefs = activity.getSharedPreferences("WaGlobal", Context.MODE_PRIVATE);
        if (prefs.getBoolean("need_restart", false)) {
            prefs.edit().putBoolean("need_restart", false).commit();
            new AlertDialogWpp(activity).
                    setMessage(activity.getString(ResId.string.restart_wpp)).
                    setPositiveButton(activity.getString(ResId.string.yes), (dialog, which) -> {
                        Utils.doRestart(activity);
                    })
                    .setNegativeButton(activity.getString(ResId.string.no), null)
                    .show();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }
}
