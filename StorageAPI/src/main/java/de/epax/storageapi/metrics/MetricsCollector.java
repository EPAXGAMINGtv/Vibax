package de.epax.storageapi.metrics;

import de.epax.storageapi.logging.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

public class MetricsCollector {
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final ConcurrentLinkedQueue<Long> latencySamples = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastSecondRequests = new AtomicLong(0);
    private final RingBuffer latencyRingBuffer = new RingBuffer(1000);
    private final ScheduledExecutorService reporter;
    private volatile long startTime = System.currentTimeMillis();

    public MetricsCollector() {
        reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Metrics-Reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleAtFixedRate(this::report, 60, 60, TimeUnit.SECONDS);
    }

    public void recordRequest() {
        totalRequests.incrementAndGet();
        lastSecondRequests.incrementAndGet();
    }

    public void recordSuccess() {
        // success counted in recordRequest
    }

    public void recordFailure() {
        failedRequests.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordLatency(long latencyMs) {
        latencyRingBuffer.add(latencyMs);
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getFailedRequests() {
        return failedRequests.get();
    }

    public double getAverageLatency() {
        if (latencyRingBuffer.isEmpty()) return 0.0;
        return latencyRingBuffer.getAverage();
    }

    public double getRequestsPerSecond() {
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        return elapsedSec > 0 ? (double) totalRequests.get() / elapsedSec : 0.0;
    }

    public double getErrorRate() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : (double) failedRequests.get() / total;
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getCacheMisses() {
        return cacheMisses.get();
    }

    public double getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total == 0 ? 0.0 : (double) cacheHits.get() / total;
    }

    public Map<String, Object> getSnapshot() {
        Map<String, Object> snap = new java.util.HashMap<>();
        snap.put("totalRequests", totalRequests.get());
        snap.put("failedRequests", failedRequests.get());
        snap.put("averageLatencyMs", getAverageLatency());
        snap.put("requestsPerSecond", getRequestsPerSecond());
        snap.put("errorRate", getErrorRate());
        snap.put("cacheHits", cacheHits.get());
        snap.put("cacheMisses", cacheMisses.get());
        snap.put("cacheHitRate", getCacheHitRate());
        snap.put("uptimeSeconds", (System.currentTimeMillis() - startTime) / 1000);
        return snap;
    }

    private void report() {
        if (true) {
            Logger.debug("=== Metrics Report ===");
            Logger.debug("Total Requests: " + totalRequests.get());
            Logger.debug("Failed: " + failedRequests.get());
            Logger.debug("Avg Latency: " + String.format("%.2f", getAverageLatency()) + "ms");
            Logger.debug("RPS: " + String.format("%.2f", getRequestsPerSecond()));
            Logger.debug("Error Rate: " + String.format("%.2f%%", getErrorRate() * 100));
        }
    }

    public void shutdown() {
        reporter.shutdown();
    }

    // Simple ring buffer for latency tracking
    private static class RingBuffer {
        private final long[] buffer;
        private int head = 0;
        private int count = 0;
        private long sum = 0;

        public RingBuffer(int capacity) {
            this.buffer = new long[capacity];
        }

        public synchronized void add(long value) {
            if (count < buffer.length) {
                buffer[count++] = value;
                sum += value;
            } else {
                int idx = head % buffer.length;
                sum -= buffer[idx];
                buffer[idx] = value;
                sum += value;
                head++;
            }
        }

        public synchronized double getAverage() {
            if (count == 0) return 0.0;
            return (double) sum / count;
        }

        public synchronized boolean isEmpty() {
            return count == 0;
        }
    }
}
