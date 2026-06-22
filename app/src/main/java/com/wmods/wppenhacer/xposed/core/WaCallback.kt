package com.wmods.wppenhacer.xposed.core

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import com.wmods.wppenhacer.xposed.core.ActivityStateRegistry.cleanup
import com.wmods.wppenhacer.xposed.core.ActivityStateRegistry.updateState
import com.wmods.wppenhacer.xposed.core.WppCore.ActivityChangeState
import com.wmods.wppenhacer.xposed.core.WppCore.listenerActivity
import java.util.function.Consumer

class WaCallback : ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        WppCore.mCurrentActivity = activity
        updateState(activity, ActivityChangeState.ChangeType.CREATED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.CREATED)
    }

    override fun onActivityStarted(activity: Activity) {
        WppCore.mCurrentActivity = activity
        updateState(activity, ActivityChangeState.ChangeType.STARTED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.STARTED)
    }

    override fun onActivityResumed(activity: Activity) {
        WppCore.mCurrentActivity = activity
        updateState(activity, ActivityChangeState.ChangeType.RESUMED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.RESUMED)
    }

    override fun onActivityPaused(activity: Activity) {
        updateState(activity, ActivityChangeState.ChangeType.PAUSED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.PAUSED)
    }

    override fun onActivityStopped(activity: Activity) {
        updateState(activity, ActivityChangeState.ChangeType.ENDED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.ENDED)
    }

    override fun onActivityDestroyed(activity: Activity) {
        updateState(activity, ActivityChangeState.ChangeType.DESTROYED)
        triggerActivityState(activity, ActivityChangeState.ChangeType.DESTROYED)
        cleanup()
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    companion object {
        private fun triggerActivityState(activity: Activity, type: ActivityChangeState.ChangeType) {
            listenerActivity.forEach(Consumer { listener: ActivityChangeState? ->
                listener!!.onChange(
                    activity,
                    type
                )
            })
        }
    }
}
