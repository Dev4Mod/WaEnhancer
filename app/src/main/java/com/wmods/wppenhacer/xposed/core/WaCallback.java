package com.wmods.wppenhacer.xposed.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WaCallback implements Application.ActivityLifecycleCallbacks {
    private static void triggerActivityState(@NonNull Activity activity, WppCore.ActivityChangeState.ChangeType type) {
        WppCore.listenerAcitivity.forEach((listener) -> listener.onChange(activity, type));
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        WppCore.mCurrentActivity = activity;
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.CREATED);
        WppCore.activities.add(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        WppCore.mCurrentActivity = activity;
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.STARTED);
        WppCore.activities.add(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        WppCore.mCurrentActivity = activity;
        WppCore.activities.add(activity);
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.RESUMED);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.PAUSED);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        triggerActivityState(activity, WppCore.ActivityChangeState.ChangeType.ENDED);
        WppCore.activities.remove(activity);
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        WppCore.activities.remove(activity);
    }
}
