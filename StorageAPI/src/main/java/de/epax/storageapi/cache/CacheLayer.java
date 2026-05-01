package de.epax.storageapi.cache;

import de.epax.storageapi.logging.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class CacheLayer {
    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final long defaultTtl;
    private final ScheduledExecutorService cleaner;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final int maxSize;

    public CacheLayer(int maxSize, long defaultTtlMs) {
        this.maxSize = maxSize;
        this.defaultTtl = defaultTtlMs;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Cache-Cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictExpired, defaultTtl, defaultTtl / 2, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return (T) entry.value;
    }

    public void put(String key, Object value, long ttlMs) {
        if (cache.size() >= maxSize) {
            evictLRU();
        }
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMs));
    }

    public boolean invalidate(String key) {
        return cache.remove(key) != null;
    }

    public void clear() {
        cache.clear();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    private void evictLRU() {
        if (!cache.isEmpty()) {
            String oldestKey = cache.entrySet().stream()
                    .min(Map.Entry.comparingByValue((e1, e2) -> Long.compare(e1.timestamp, e2.timestamp)))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestKey != null) {
                cache.remove(oldestKey);
            }
        }
    }

    private static class CacheEntry<T> {
        final T value;
        final long timestamp;

        CacheEntry(T value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return timestamp < now;
        }
    }

    public void shutdown() {
        cleaner.shutdown();
    }
}
