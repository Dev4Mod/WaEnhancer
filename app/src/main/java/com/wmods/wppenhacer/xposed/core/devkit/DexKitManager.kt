package com.wmods.wppenhacer.xposed.core.devkit

import org.luckypray.dexkit.DexKitBridge
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DexKitManager(private val apkPath: String) {

    fun interface BridgeCallable<T> {
        @Throws(Exception::class)
        fun call(bridge: DexKitBridge): T
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "dexkit-idle").apply { isDaemon = true }
    }

    private val lock = ReentrantLock()

    private var bridge: DexKitBridge? = null
    private var leases = 0
    private var startupPhase = true
    private var closed = false
    private var pendingClose: ScheduledFuture<*>? = null
    private var closeGeneration = 0L

    @Throws(Exception::class)
    fun <T> withBridge(callable: BridgeCallable<T>): T {
        val first = acquire()
        try {
            return callable.call(first)
        } catch (e: IllegalStateException) {
            if (first.isValid) throw e
        } finally {
            release()
        }
        val retry = acquire()
        try {
            return callable.call(retry)
        } finally {
            release()
        }
    }

    fun releaseStartupBridge() {
        lock.withLock {
            startupPhase = false
            if (leases == 0) scheduleIdleCloseLocked()
        }
    }

    fun close() {
        lock.withLock {
            closed = true
            startupPhase = false
            cancelIdleCloseLocked()
            closeBridgeLocked()
        }
        scheduler.shutdown()
    }

    private fun acquire(): DexKitBridge = lock.withLock {
        check(!closed) { "DexKitManager is closed" }
        cancelIdleCloseLocked()
        val current = bridge?.takeIf { it.isValid } ?: createBridge().also { bridge = it }
        leases++
        current
    }

    private fun release() {
        lock.withLock {
            leases--
            if (leases == 0 && !startupPhase) scheduleIdleCloseLocked()
        }
    }

    private fun createBridge(): DexKitBridge {
        return try {
            DexKitBridge.create(apkPath)
        } catch (e: Throwable) {
            throw RuntimeException("Failed to initialize DexKit bridge", e)
        }
    }

    private fun cancelIdleCloseLocked() {
        closeGeneration++
        pendingClose?.cancel(false)
        pendingClose = null
    }

    private fun scheduleIdleCloseLocked() {
        if (closed || bridge == null) return
        cancelIdleCloseLocked()
        val generation = closeGeneration
        pendingClose = scheduler.schedule({
            lock.withLock {
                if (generation == closeGeneration && leases == 0 && !startupPhase) {
                    pendingClose = null
                    closeBridgeLocked()
                }
            }
        }, IDLE_CLOSE_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun closeBridgeLocked() {
        val toClose = bridge ?: return
        bridge = null
        try {
            toClose.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val IDLE_CLOSE_DELAY_MS = 30_000L
    }
}
