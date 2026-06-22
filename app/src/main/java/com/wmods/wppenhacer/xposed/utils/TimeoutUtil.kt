package com.wmods.wppenhacer.xposed.utils

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object TimeoutUtil {

    private val scheduler: ScheduledExecutorService = ScheduledThreadPoolExecutor(1)

    @JvmStatic
    fun <T> withTimeout(future: CompletableFuture<T>, timeout: Long, unit: TimeUnit): CompletableFuture<T> {
        val timeoutFuture = CompletableFuture<T>()

        scheduler.schedule({
            timeoutFuture.completeExceptionally(
                TimeoutException("Operation exceeded the time limit of $timeout $unit")
            )
        }, timeout, unit)

        return CompletableFuture.anyOf(future, timeoutFuture)
            .thenApply { result -> result as T }
            .exceptionally { ex ->
                future.cancel(true)
                throw RuntimeException(ex)
            }
    }
}
