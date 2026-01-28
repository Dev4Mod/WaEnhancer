package com.wmods.wppenhacer.xposed.core;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class ActivityStateRegistry {

    private static final Map<Activity, WppCore.ActivityChangeState.ChangeType> activityStates =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<String, WeakReference<Activity>> activityBySimpleName =
            Collections.synchronizedMap(new HashMap<>());

    public static void updateState(Activity activity, WppCore.ActivityChangeState.ChangeType type) {
        if (activity == null) return;
        activityStates.put(activity, type);
        activityBySimpleName.put(activity.getClass().getSimpleName(), new WeakReference<>(activity));
    }

    public static WppCore.ActivityChangeState.ChangeType getState(Activity activity) {
        if (activity == null) return null;
        return activityStates.get(activity);
    }

    public static WppCore.ActivityChangeState.ChangeType getStateBySimpleName(String simpleName) {
        WeakReference<Activity> ref = activityBySimpleName.get(simpleName);
        if (ref == null) return null;
        Activity activity = ref.get();
        if (activity == null) {
            activityBySimpleName.remove(simpleName);
            return null;
        }
        return activityStates.get(activity);
    }

    public static Activity getActivityBySimpleName(String simpleName) {
        WeakReference<Activity> ref = activityBySimpleName.get(simpleName);
        if (ref == null) return null;
        Activity activity = ref.get();
        if (activity == null) {
            activityBySimpleName.remove(simpleName);
        }
        return activity;
    }

    public static boolean isInState(Activity activity, WppCore.ActivityChangeState.ChangeType type) {
        return type == getState(activity);
    }

    public static boolean isAnyActivityInState(WppCore.ActivityChangeState.ChangeType type) {
        return activityStates.containsValue(type);
    }

    public static List<Activity> getActivitiesInState(WppCore.ActivityChangeState.ChangeType type) {
        List<Activity> result = new ArrayList<>();
        for (Map.Entry<Activity, WppCore.ActivityChangeState.ChangeType> entry : activityStates.entrySet()) {
            if (entry.getValue() == type) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static void cleanup() {
        activityBySimpleName.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    public static int getTrackedCount() {
        return activityStates.size();
    }
}