package de.epax.storageapi.retry;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class RetryPolicy {
    public enum BackoffStrategy { LINEAR, EXPONENTIAL, EXPONENTIAL_WITH_JITTER }

    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final BackoffStrategy strategy;
    private final Predicate<Exception> retryCondition;

    public static class Builder {
        private int maxAttempts = 3;
        private long initialDelayMs = 100;
        private long maxDelayMs = 10000;
        private double multiplier = 2.0;
        private BackoffStrategy strategy = BackoffStrategy.EXPONENTIAL_WITH_JITTER;
        private Predicate<Exception> retryCondition = e -> e instanceof IOException ||
                e instanceof StorageAPI.StorageAPIException && ((StorageAPI.StorageAPIException) e).getCode() != StorageAPI.ErrorCode.FILE_NOT_FOUND;

        public Builder maxAttempts(int attempts) {
            this.maxAttempts = attempts;
            return this;
        }

        public Builder initialDelay(long delayMs) {
            this.initialDelayMs = delayMs;
            return this;
        }

        public Builder maxDelay(long delayMs) {
            this.maxDelayMs = delayMs;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder backoffStrategy(BackoffStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder retryOn(Predicate<Exception> condition) {
            this.retryCondition = condition;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(maxAttempts, initialDelayMs, maxDelayMs, multiplier, strategy, retryCondition);
        }
    }

    private RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs,
                       double multiplier, BackoffStrategy strategy, Predicate<Exception> retryCondition) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.strategy = strategy;
        this.retryCondition = retryCondition;
    }

    public static <T> T executeWithRetry(Callable<T> callable, int maxAttempts, long initialDelayMs) throws Exception {
        return new RetryPolicy(maxAttempts, initialDelayMs, 10000, 2.0,
                BackoffStrategy.EXPONENTIAL_WITH_JITTER, e -> true).execute(callable);
    }

    public <T> T execute(Callable<T> callable) throws Exception {
        Exception lastException = null;
        long delay = initialDelayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (attempt > 1) {
                    Thread.sleep(delay);
                }
                return callable.call();
            } catch (Exception e) {
                lastException = e;
                if (!retryCondition.test(e) || attempt == maxAttempts) {
                    break;
                }
                delay = calculateNextDelay(delay, attempt);
                Logger.warn("Retry attempt " + attempt + " failed: " + e.getMessage() + ", next delay: " + delay + "ms");
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("All retry attempts failed");
    }



    private long calculateNextDelay(long currentDelay, int attempt) {
        long nextDelay;
        switch (strategy) {
            case LINEAR -> nextDelay = currentDelay + initialDelayMs;
            case EXPONENTIAL -> nextDelay = (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));
            case EXPONENTIAL_WITH_JITTER -> {
                long exponential = (long) (initialDelayMs * Math.pow(multiplier, attempt - 1));
                long jitter = ThreadLocalRandom.current().nextLong(exponential / 2);
                nextDelay = Math.min(exponential + jitter, maxDelayMs);
            }
            default -> nextDelay = currentDelay;
        }
        return Math.min(nextDelay, maxDelayMs);
    }
}
