package com.wmods.wppenhacer.util

import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

object PerfMonitor {
    @JvmField var enabled = true
    @JvmField var logThresholdMs = 10L

    private data class Stat(var count: Long = 0, var totalMs: Long = 0, var maxMs: Long = 0)
    private val stats = ConcurrentHashMap<String, Stat>()

    fun interface ThrowingRunnable {
        @Throws(Throwable::class)
        fun run()
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun track(tag: String, block: ThrowingRunnable) {
        if (!enabled) {
            try {
                block.run()
            } catch (t: Throwable) {
                throw t
            }
            return
        }
        val start = SystemClock.uptimeMillis()
        try {
            block.run()
        } finally {
            record(tag, start)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun <T> track(tag: String, block: Callable<T>): T {
        if (!enabled) return block.call()
        val start = SystemClock.uptimeMillis()
        try { return block.call() }
        finally {
            record(tag, start)
        }
    }

    fun record(tag: String, start: Long) {
        val dt = SystemClock.uptimeMillis() - start
        val stat = stats.getOrPut(tag) { Stat() }
        stat.count++
        stat.totalMs += dt
        if (dt > stat.maxMs) stat.maxMs = dt
        if (dt >= logThresholdMs && Looper.myLooper() == Looper.getMainLooper()) {
            XposedBridge.log("WAE PERF: [$tag] took ${dt}ms on MAIN thread")
        }
    }
}
