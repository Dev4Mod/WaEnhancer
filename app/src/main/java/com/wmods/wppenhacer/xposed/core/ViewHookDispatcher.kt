package com.wmods.wppenhacer.xposed.core

import android.view.View
import android.widget.FrameLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.util.concurrent.CopyOnWriteArrayList

object ViewHookDispatcher {

    fun interface ViewListener {
        fun onView(view: View)
    }

    fun interface SetFlagsFilter {
        fun filter(view: View, flags: Int, mask: Int): Int
    }

    fun interface MeasureListener {
        fun onMeasured(view: FrameLayout)
    }

    class DeferredSetFlags internal constructor(
        private val packageParam: PackageParam,
        private val filter: SetFlagsFilter
    ) {
        @Volatile
        private var active = false

        fun activate() {
            if (active) return
            synchronized(this) {
                if (active) return
                onSetFlags(packageParam, filter)
                active = true
            }
        }
    }

    private val attachListeners = CopyOnWriteArrayList<ViewListener>()
    private val detachListeners = CopyOnWriteArrayList<ViewListener>()
    private val setFlagsFilters = CopyOnWriteArrayList<SetFlagsFilter>()
    private val frameMeasureListeners = CopyOnWriteArrayList<MeasureListener>()

    @Volatile
    private var attachHooked = false

    @Volatile
    private var detachHooked = false

    @Volatile
    private var setFlagsHooked = false

    @Volatile
    private var frameMeasureHooked = false

    fun onAttachedToWindow(packageParam: PackageParam, listener: ViewListener) {
        attachListeners.add(listener)
        if (attachHooked) return
        synchronized(this) {
            if (attachHooked) return
            hookViewLifecycle(packageParam, "onAttachedToWindow", attachListeners)
            attachHooked = true
        }
    }

    fun onDetachedFromWindow(packageParam: PackageParam, listener: ViewListener) {
        detachListeners.add(listener)
        if (detachHooked) return
        synchronized(this) {
            if (detachHooked) return
            hookViewLifecycle(packageParam, "onDetachedFromWindow", detachListeners)
            detachHooked = true
        }
    }

    fun onSetFlags(packageParam: PackageParam, filter: SetFlagsFilter) {
        setFlagsFilters.add(filter)
        if (setFlagsHooked) return
        synchronized(this) {
            if (setFlagsHooked) return
            packageParam.apply {
                View::class.java.resolve().firstMethod {
                    name = "setFlags"
                    parameters(Int::class, Int::class)
                }.hook {
                    before {
                        val view = instance as View
                        val original = args[0] as Int
                        val mask = args[1] as Int
                        var flags = original
                        for (f in setFlagsFilters) {
                            try {
                                flags = f.filter(view, flags, mask)
                            } catch (t: Throwable) {
                                YukiLog.log(t)
                            }
                        }
                        if (flags != original) args[0] = flags
                    }
                }
            }
            setFlagsHooked = true
        }
    }

    fun deferSetFlags(packageParam: PackageParam, filter: SetFlagsFilter) =
        DeferredSetFlags(packageParam, filter)

    fun onFrameLayoutMeasured(packageParam: PackageParam, listener: MeasureListener) {
        frameMeasureListeners.add(listener)
        if (frameMeasureHooked) return
        synchronized(this) {
            if (frameMeasureHooked) return
            packageParam.apply {
                FrameLayout::class.java.resolve().firstMethod {
                    name = "onMeasure"
                    parameters(Int::class, Int::class)
                }.hook {
                    after {
                        val view = instance as FrameLayout
                        for (l in frameMeasureListeners) {
                            try {
                                l.onMeasured(view)
                            } catch (t: Throwable) {
                                YukiLog.log(t)
                            }
                        }
                    }
                }
            }
            frameMeasureHooked = true
        }
    }

    private fun hookViewLifecycle(
        packageParam: PackageParam,
        methodName: String,
        listeners: List<ViewListener>
    ) {
        packageParam.apply {
            View::class.java.resolve().firstMethod {
                name = methodName
                emptyParameters()
            }.hook {
                after {
                    val view = instance as View
                    for (l in listeners) {
                        try {
                            l.onView(view)
                        } catch (t: Throwable) {
                            YukiLog.log(t)
                        }
                    }
                }
            }
        }
    }
}
