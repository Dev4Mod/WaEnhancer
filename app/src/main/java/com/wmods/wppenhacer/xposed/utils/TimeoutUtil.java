package com.wmods.wppenhacer.xposed.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TimeoutUtil {

    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    /**
     * Adds a timeout to a CompletableFuture
     * @param future The original CompletableFuture
     * @param timeout Timeout duration
     * @param unit Time unit
     * @param <T> Type of the result
     * @return New CompletableFuture with timeout
     */
    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();

        // Schedules a task to complete the future with an exception after the timeout
        scheduler.schedule(() -> {
            timeoutFuture.completeExceptionally(
                    new java.util.concurrent.TimeoutException("Operation exceeded the time limit of " + timeout + " " + unit));
        }, timeout, unit);

        // Returns the first to complete (either the original or the timeout)
        return CompletableFuture.anyOf(future, timeoutFuture)
                .thenApply(result -> (T) result)
                .exceptionally(ex -> {
                    // Cancels the original future if a timeout occurs
                    future.cancel(true);
                    throw new RuntimeException(ex);
                });
    }

}
