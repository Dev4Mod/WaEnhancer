package com.wmods.wppenhacer.xposed.core

import android.app.Activity
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

object ActivityStateRegistry {
    private val activityStates: MutableMap<Activity?, WppCore.ActivityChangeState.ChangeType?> =
        Collections.synchronizedMap<Activity?, WppCore.ActivityChangeState.ChangeType?>(WeakHashMap<Activity?, WppCore.ActivityChangeState.ChangeType?>())

    private val activityBySimpleName: MutableMap<String?, WeakReference<Activity?>?> =
        Collections.synchronizedMap<String?, WeakReference<Activity?>?>(HashMap<String?, WeakReference<Activity?>?>())

    @JvmStatic
    fun updateState(activity: Activity?, type: WppCore.ActivityChangeState.ChangeType?) {
        if (activity == null) return
        activityStates[activity] = type
        activityBySimpleName[activity.javaClass.simpleName] = WeakReference<Activity?>(activity)
    }

    @JvmStatic
    fun cleanup() {
        activityBySimpleName.entries.removeIf { entry: MutableMap.MutableEntry<String?, WeakReference<Activity?>?>? -> entry!!.value!!.get() == null }
    }

}