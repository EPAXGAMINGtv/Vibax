package de.epax.storageapi.circuit;

import de.epax.storageapi.logging.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final double failureThreshold;
    private final int minCalls;
    private final long timeoutMs;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile State state = State.CLOSED;
    private final Object lock = new Object();

    public CircuitBreaker(String name, double failureThreshold, int minCalls, long timeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.minCalls = minCalls;
        this.timeoutMs = timeoutMs;
    }

    public boolean allowRequest() {
        synchronized (lock) {
            if (state == State.CLOSED) {
                return true;
            }
            if (state == State.OPEN) {
                long elapsed = System.currentTimeMillis() - lastFailureTime.get();
                if (elapsed > timeoutMs) {
                    state = State.HALF_OPEN;
                    Logger.info("CircuitBreaker [" + name + "] entering HALF_OPEN state");
                    return true;
                }
                return false;
            }
            // HALF_OPEN - allow one test request
            return true;
        }
    }

    public void recordSuccess() {
        synchronized (lock) {
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                successCount.set(1);
                failureCount.set(0);
                Logger.info("CircuitBreaker [" + name + "] recovered, state CLOSED");
            } else if (state == State.CLOSED) {
                successCount.incrementAndGet();
                failureCount.set(0); // reset on success
            }
        }
    }

    public void recordFailure() {
        synchronized (lock) {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            if (state == State.HALF_OPEN) {
                state = State.OPEN;
                Logger.warn("CircuitBreaker [" + name + "] failed in HALF_OPEN, state OPEN");
                return;
            }

            if (state == State.CLOSED) {
                int failures = failureCount.get();
                int successes = successCount.get();
                int totalCalls = failures + successes;

                if (totalCalls >= minCalls) {
                    double failureRate = (double) failures / totalCalls;
                    if (failureRate >= failureThreshold) {
                        state = State.OPEN;
                        Logger.warn("CircuitBreaker [" + name + "] failure rate " + String.format("%.2f", failureRate) + " >= " + failureThreshold + ", state OPEN");
                    }
                }
            }
        }
    }

    public State getState() {
        return state;
    }

    public double getFailureRate() {
        int failures = failureCount.get();
        int successes = successCount.get();
        return failures + successes == 0 ? 0.0 : (double) failures / (failures + successes);
    }

    public void reset() {
        synchronized (lock) {
            state = State.CLOSED;
            successCount.set(0);
            failureCount.set(0);
        }
    }
}
