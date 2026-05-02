package de.epax.storageapi.health;

import de.epax.storageapi.ServerConfig;
import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.circuit.CircuitBreaker;
import de.epax.storageapi.internet.HttpConnection;
import de.epax.storageapi.logging.Logger;
import de.epax.storageapi.pool.ServerPool;
import de.epax.storageapi.events.EventEmitter;
import de.epax.storageapi.events.StorageEvent;
import de.epax.storageapi.metrics.MetricsCollector;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class HealthMonitor {
    public enum HealthStatus {HEALTHY, DEGRADED, DOWN}

    private final ServerPool serverPool;
    private final MetricsCollector metrics;
    private final EventEmitter eventEmitter;
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> latencies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> uptimeStart = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private volatile boolean running = false;

    public HealthMonitor(ServerPool serverPool, MetricsCollector metrics, EventEmitter eventEmitter) {
        this.serverPool = serverPool;
        this.metrics = metrics;
        this.eventEmitter = eventEmitter;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Health-Monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        // Initial uptime tracking
        for (String server : serverPool.getServerNames()) {
            uptimeStart.put(server, new AtomicLong(System.currentTimeMillis()));
            circuitBreakers.put(server, new CircuitBreaker(server, 0.5, 5, 30000));
        }
        executor.scheduleAtFixedRate(this::performHealthChecks, 0, 5, TimeUnit.SECONDS);
        // Schedule system info updates every 30 seconds
        executor.scheduleAtFixedRate(this::updateSystemInfoForAllServers, 5, 30, TimeUnit.SECONDS);
        Logger.info("HealthMonitor started");
    }

    public void stop() {
        running = false;
        executor.shutdown();
        Logger.info("HealthMonitor stopped");
    }

    private void performHealthChecks() {
        if (!running) return;

        var servers = serverPool.getAllServers();
        for (var entry : servers.entrySet()) {
            String serverName = entry.getKey();
            String serverUrl = entry.getValue();
            long start = System.currentTimeMillis();
            boolean online = false;

            try {
                online = serverPool.ping(serverName);
            } catch (Exception e) {
                online = false;
            }

            long latency = System.currentTimeMillis() - start;

            // Track latency
            AtomicLong latencyAcc = latencies.computeIfAbsent(serverName, k -> new AtomicLong(0));
            latencyAcc.addAndGet(latency);

            CircuitBreaker cb = circuitBreakers.get(serverName);
            if (cb == null) {
                cb = new CircuitBreaker(serverName, 0.5, 5, 30000);
                circuitBreakers.put(serverName, cb);
            }

            // Update circuit breaker
            if (online) {
                cb.recordSuccess();
            } else {
                cb.recordFailure();
            }

            // Update server health in server pool
            serverPool.updateServerHealth(serverName, online);

            // Emit events on state changes
            boolean wasUp = uptimeStart.containsKey(serverName);
            if (online && !wasUp) {
                uptimeStart.put(serverName, new AtomicLong(System.currentTimeMillis()));
                eventEmitter.emit(StorageEvent.serverUp(serverName));
            } else if (!online && wasUp) {
                uptimeStart.remove(serverName);
                eventEmitter.emit(StorageEvent.serverDown(serverName));
            }
        }
    }

    public HealthStatus getStatus(String serverName) {
        CircuitBreaker cb = circuitBreakers.get(serverName);
        if (cb == null) return HealthStatus.DOWN;
        return switch (cb.getState()) {
            case CLOSED -> HealthStatus.HEALTHY;
            case HALF_OPEN -> HealthStatus.DEGRADED;
            case OPEN -> HealthStatus.DOWN;
        };
    }

    public double getLatency(String serverName) {
        AtomicLong acc = latencies.get(serverName);
        if (acc == null) return -1;
        // Return average - would need more sophisticated tracking
        return acc.get() / 1000.0;
    }

    public long getUptime(String serverName) {
        AtomicLong start = uptimeStart.get(serverName);
        if (start == null) return 0;
        return System.currentTimeMillis() - start.get();
    }

    public CircuitBreaker getCircuitBreaker(String serverName) {
        return circuitBreakers.get(serverName);
    }

    public void updateSystemInfoForAllServers() {
        var servers = serverPool.getAllServers();
        for (var entry : servers.entrySet()) {
            String serverName = entry.getKey();
            try {
                double latency = getLatency(serverName);
                if (latency > 0) {
                    for (Map.Entry<Integer, ServerConfig> serverEntry : StorageAPI.getServers().entrySet()) {
                        if (serverEntry.getValue().name.equals(serverName)) {
                            ServerConfig server = serverEntry.getValue();
                            server.avgLatency = latency;
                            server.lastSystemInfoUpdate = System.currentTimeMillis();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.debug("Could not update latency for " + serverName);
            }
            
            // Update system info (free/total space) from server
            try {
                for (Map.Entry<Integer, ServerConfig> serverEntry : StorageAPI.getServers().entrySet()) {
                    if (serverEntry.getValue().name.equals(serverName)) {
                        ServerConfig server = serverEntry.getValue();
                        String serverAddress = server.ip;
                        String url = "http://" + serverAddress + "/info?path=/";
                        String response = HttpConnection.sendRequest(url, "GET", server.passwordHash, null, null, null);
                        
                        // Parse response to extract freeSpace and totalSpace
                        if (response != null) {
                            // Expected JSON format: {"freeSpace":12345,"totalSpace":67890,...}
                            long freeSpace = -1;
                            long totalSpace = -1;
                            
                            int freeIndex = response.indexOf("\"freeSpace\":");
                            if (freeIndex != -1) {
                                int start = freeIndex + "\"freeSpace\":".length();
                                int end = response.indexOf(",", start);
                                if (end == -1) end = response.indexOf("}", start);
                                if (end != -1) {
                                    try {
                                        freeSpace = Long.parseLong(response.substring(start, end).trim());
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            
                            int totalIndex = response.indexOf("\"totalSpace\":");
                            if (totalIndex != -1) {
                                int start = totalIndex + "\"totalSpace\":".length();
                                int end = response.indexOf(",", start);
                                if (end == -1) end = response.indexOf("}", start);
                                if (end != -1) {
                                    try {
                                        totalSpace = Long.parseLong(response.substring(start, end).trim());
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                            
                            if (freeSpace != -1) server.freeSpace = freeSpace;
                            if (totalSpace != -1) server.totalSpace = totalSpace;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.debug("Could not update system info for " + serverName + ": " + e.getMessage());
            }
        }
    }
}