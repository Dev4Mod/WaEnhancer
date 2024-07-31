package com.wmods.wppenhacer.xposed.utils;

import java.util.Optional;
import java.util.function.Supplier;

public class RunCatchingUtil {

    public static <T> Result<T> runCatching(Supplier<T> block) {
        try {
            return Result.success(block.get());
        } catch (Throwable e) {
            return Result.failure(e);
        }
    }

    public static class Result<T> {
        private final T value;
        private final Throwable exception;

        private Result(T value, Throwable exception) {
            this.value = value;
            this.exception = exception;
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(value, null);
        }

        public static <T> Result<T> failure(Throwable exception) {
            return new Result<>(null, exception);
        }

        public Optional<T> getValue() {
            return Optional.ofNullable(value);
        }

        public Optional<Throwable> getException() {
            return Optional.ofNullable(exception);
        }

        public boolean isSuccess() {
            return exception == null;
        }

        public boolean isFailure() {
            return exception != null;
        }
    }
}