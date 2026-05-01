package de.epax.storageapi;

import de.epax.storageapi.cache.CacheLayer;
import de.epax.storageapi.circuit.CircuitBreaker;
import de.epax.storageapi.events.EventEmitter;
import de.epax.storageapi.events.StorageEvent;
import de.epax.storageapi.health.HealthMonitor;
import de.epax.storageapi.internet.HttpConnection;
import de.epax.storageapi.logging.Logger;
import de.epax.storageapi.manager.PropertiesManager;
import de.epax.storageapi.metrics.MetricsCollector;
import de.epax.storageapi.pool.ServerPool;
import de.epax.storageapi.retry.RetryPolicy;
import de.epax.storageapi.batch.BatchRequest;
import de.epax.storageapi.batch.BatchResponse;
import de.epax.storageapi.server.isOnlineServer;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StorageAPI {

    // Core components
    private static PropertiesManager propertiesManager = new PropertiesManager();
    static final Map<Integer, ServerConfig> servers = new ConcurrentHashMap<>();
    private static ServerPool serverPool;
    private static EventEmitter eventEmitter;
    private static HealthMonitor healthMonitor;
    private static MetricsCollector metrics;
    private static CacheLayer cache;
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "StorageAPI-Scheduler");
        t.setDaemon(true);
        return t;
    });

    // Configuration
    private static final boolean ENABLE_CACHE = true;
    private static final long CACHE_TTL_MS = 60000; // 1 minute
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INITIAL_DELAY_MS = 100;
    private static final double CIRCUIT_BREAKER_THRESHOLD = 0.5; // 50% failure rate
    private static final int CIRCUIT_BREAKER_MIN_CALLS = 5;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 30000; // 30 seconds

    // Statistics
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        InitStorageAPI(true);

        // Example usage: Find server containing test.txt
        String foundServer = null;
        for (ServerConfig server : servers.values()) {
            if (server.online) {
                try {
                    String url = "http://" + server.ip + "/exists?path=test.txt";
                    String response = HttpConnection.sendRequest(url, "GET", server.passwordHash, null, null, null);
                    if (response != null && response.contains("EXISTS")) {
                        foundServer = server.name;
                        break;
                    }
                } catch (Exception e) {
                    // continue
                }
            }
        }

        Logger.info("File found on server: " + (foundServer != null ? foundServer : "none"));

        if (foundServer != null) {
            String content = readFile(foundServer, "test.txt");
            Logger.info("Content: " + content);
        }

        // Print metrics
        Logger.info("=== Metrics ===");
        Logger.info("Total Requests: " + metrics.getTotalRequests());
        Logger.info("Failed Requests: " + metrics.getFailedRequests());
        Logger.info("Average Latency: " + metrics.getAverageLatency() + "ms");
        Logger.info("Cache Hit Rate: " + String.format("%.2f%%", metrics.getCacheHitRate() * 100));
    }

    public static void InitStorageAPI(boolean makeStorageServerOnlineHttpServer) throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Initializing StorageAPI...");

        // Initialize core components
        propertiesManager = new PropertiesManager();
        try {
            propertiesManager.load("api", "storageapi.properties");
        } catch (IOException e) {
            Logger.error("Failed to load properties: " + e.getMessage());
        }

        // Default server config
        if (!propertiesManager.exists("api", "serverip-1")) {
            propertiesManager.set("api", "serverip-1", "127.0.0.1:8080");
        }
        if (!propertiesManager.exists("api", "serverpwhash-1")) {
            propertiesManager.set("api", "serverpwhash-1", "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4");
        }
        propertiesManager.save("api", "storageapi.properties");

        // Load servers
        Properties props = propertiesManager.getProperties("api");
        if (props != null) {
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("serverip-")) {
                    int id = Integer.parseInt(key.split("-")[1]);
                    String ip = props.getProperty(key);
                    String hash = props.getProperty("serverpwhash-" + id);
                    if (hash != null) {
                        servers.put(id, new ServerConfig(ip, hash));
                    }
                }
            }
        }

        // Initialize components
        cache = new CacheLayer(1000, CACHE_TTL_MS);
        eventEmitter = new EventEmitter();
        metrics = new MetricsCollector();
        serverPool = new ServerPool(servers);

        // Initialize health monitor
        healthMonitor = new HealthMonitor(serverPool, metrics, eventEmitter);
        healthMonitor.start();

        // Start periodic checks
        scheduler.scheduleAtFixedRate(() -> {
            checkServers();
            checkServerNames();
        }, 0, 10, TimeUnit.SECONDS);

        // Start online server if requested
        if (makeStorageServerOnlineHttpServer) {
            isOnlineServer areStorageServersOnlineServer = new isOnlineServer(1000, 10);
            areStorageServersOnlineServer.addServerHandler(new OnlineHandler(), "/online");
            areStorageServersOnlineServer.startServer();
        }

        Logger.info("StorageAPI initialized in " + (System.currentTimeMillis() - start) + "ms");
    }

    // ==================== Enhanced API Methods ====================

    public static String readFile(String serverName, String fileName) {
        return readFile(serverName, fileName, true);
    }

    public static String readFile(String serverName, String fileName, boolean useCache) {
        String cacheKey = "read:" + serverName + ":" + fileName;
        if (useCache && cache != null) {
            String cached = cache.get(cacheKey, String.class);
            if (cached != null) {
                metrics.recordCacheHit();
                return cached;
            }
        }

        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/readfile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                if (useCache && cache != null && response != null) {
                    cache.put(cacheKey, response, CACHE_TTL_MS);
                }
                metrics.recordSuccess();
                return response;
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean writeFile(String serverName, String fileName, String content) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/writefile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, content, serverName);
                cache.invalidate("read:" + serverName + ":" + fileName);
                metrics.recordSuccess();
                return response != null && response.contains("Written to");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static List<String> listFiles(String serverName, String directory) {
        return listFiles(serverName, directory, false);
    }

    public static List<String> listFiles(String serverName, String directory, boolean recursive) {
        String cacheKey = "list:" + serverName + ":" + directory + ":" + recursive;
        if (cache != null) {
            List<String> cached = cache.get(cacheKey, List.class);
            if (cached != null) {
                metrics.recordCacheHit();
                return cached;
            }
        }

        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String path = recursive ? "/listfilesrecursive?path=" : "/listfiles?path=";
                String url = "http://" + server + path + URLEncoder.encode(directory, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                if (response == null) return Collections.emptyList();

                // Parse response (simple newline-separated list)
                List<String> files = new ArrayList<>(Arrays.asList(response.split("\n")));
                files.removeIf(String::isBlank);

                if (cache != null) {
                    cache.put(cacheKey, files, CACHE_TTL_MS);
                }
                metrics.recordSuccess();
                return files;
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean deleteFile(String serverName, String fileName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/delete?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendDeleteRequest(url, serverName);
                cache.invalidate("read:" + serverName + ":" + fileName);
                cache.invalidate("list:" + serverName + ":" + (new File(fileName).getParent()));
                metrics.recordSuccess();
                return response != null && response.contains("Deleted");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean createDirectory(String serverName, String dirName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/createdirectory?path=" + URLEncoder.encode(dirName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("created");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean createFile(String serverName, String fileName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/makefile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("created");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean copy(String serverName, String source, String target) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/copy?source=" + URLEncoder.encode(source, StandardCharsets.UTF_8) +
                           "&target=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Copied");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean move(String serverName, String source, String target) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/move?source=" + URLEncoder.encode(source, StandardCharsets.UTF_8) +
                           "&target=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                cache.invalidate("read:" + serverName + ":" + source);
                cache.invalidate("list:" + serverName + ":" + (new File(source).getParent()));
                metrics.recordSuccess();
                return response != null && response.contains("Moved");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean rename(String serverName, String path, String newName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/rename?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&newname=" + URLEncoder.encode(newName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Renamed");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static String getChecksum(String serverName, String fileName, String algorithm) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/checksum?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) +
                           "&algorithm=" + algorithm;
                String response = sendRequest(url, serverName);
                // Parse JSON response to extract checksum
                if (response != null && response.contains("\"checksum\"")) {
                    String checksum = response.split("\"checksum\":\"")[1].split("\"")[0];
                    metrics.recordSuccess();
                    return checksum;
                }
                metrics.recordFailure();
                return null;
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static Map<String, Object> getFileInfo(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/info?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                // Parse info string into map
                Map<String, Object> info = new HashMap<>();
                if (response != null) {
                    for (String line : response.split("\n")) {
                        String[] parts = line.split(": ", 2);
                        if (parts.length == 2) {
                            info.put(parts[0], parts[1]);
                        }
                    }
                }
                metrics.recordSuccess();
                return info;
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static long getDirectorySize(String serverName, String path) {
        Map<String, Object> info = getFileInfo(serverName, path);
        Object sizeObj = info.get("Size");
        if (sizeObj != null) {
            try {
                return Long.parseLong(sizeObj.toString().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                // fallback
            }
        }
        return -1;
    }

    public static boolean acquireLock(String serverName, String path, String ownerId, long timeoutMs) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/lock?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&owner=" + URLEncoder.encode(ownerId, StandardCharsets.UTF_8) +
                           "&timeout=" + timeoutMs;
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Lock acquired");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean releaseLock(String serverName, String path, String ownerId) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/unlock?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&owner=" + URLEncoder.encode(ownerId, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Lock released");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static Map<String, Object> getLockStatus(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/lockstatus?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                // Parse JSON response
                return parseJsonResponse(response);
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean createVersion(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/versioncreate?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Version created");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static List<Map<String, Object>> listVersions(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/versionlist?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                // Parse JSON array
                return parseJsonArray(response);
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean restoreVersion(String serverName, String path, String versionId) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/versionrestore?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&version=" + URLEncoder.encode(versionId, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Restored");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean setMetadata(String serverName, String path, String key, String value) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/setmetadata?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8) +
                           "&value=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, "", serverName);
                metrics.recordSuccess();
                return response != null && response.contains("Metadata set");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static String getMetadata(String serverName, String path, String key) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/getmetadata?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8) +
                           "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                // Parse JSON to extract value
                if (response != null && response.contains("\"" + key + "\"")) {
                    String value = response.split(key + "\":\"")[1].split("\"")[0];
                    metrics.recordSuccess();
                    return value;
                }
                metrics.recordFailure();
                return null;
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static Map<String, String> getAllMetadata(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/getallmetadata?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                return parseJsonToMap(response);
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static List<String> searchFiles(String pattern) {
        return executeWithRetryAndCircuitBreaker(null, () -> {
            // Search across all servers
            List<String> results = new ArrayList<>();
            for (ServerConfig server : servers.values()) {
                if (!server.online) continue;
                try {
                    String url = "http://" + server.ip + "/searchfiles?pattern=" + URLEncoder.encode(pattern, StandardCharsets.UTF_8);
                    String response = sendRequest(url, server.name);
                    if (response != null) {
                        results.addAll(Arrays.asList(response.split("\n")));
                    }
                } catch (Exception e) {
                    // Continue with next server
                }
            }
            metrics.recordSuccess();
            return results;
        });
    }

    public static Map<String, Object> getServerHealth(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null) return Map.of("error", "Server not found");

        Map<String, Object> health = new HashMap<>();
        health.put("name", server.name);
        health.put("online", server.online);
        health.put("url", server.ip);
        health.put("latency", healthMonitor.getLatency(serverName));
        health.put("uptime", healthMonitor.getUptime(serverName));
        health.put("status", healthMonitor.getStatus(serverName).name());
        return health;
    }

    public static Map<String, Object> getAllServerHealth() {
        Map<String, Object> all = new HashMap<>();
        for (ServerConfig server : servers.values()) {
            all.put(server.name, getServerHealth(server.name));
        }
        return all;
    }

    public static Map<String, Object> getMetrics() {
        Map<String, Object> m = new HashMap<>();
        m.put("totalRequests", metrics.getTotalRequests());
        m.put("failedRequests", metrics.getFailedRequests());
        m.put("averageLatencyMs", metrics.getAverageLatency());
        m.put("requestsPerSecond", metrics.getRequestsPerSecond());
        m.put("errorRate", metrics.getErrorRate());
        m.put("cacheHitRate", cache != null ? cache.getHitRate() : 0.0);
        return m;
    }

    public static String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("StorageAPI Statistics\n");
        sb.append("=====================\n");
        sb.append("Servers: ").append(servers.size()).append("\n");
        sb.append("Total Requests: ").append(metrics.getTotalRequests()).append("\n");
        sb.append("Failed: ").append(metrics.getFailedRequests()).append("\n");
        sb.append("Avg Latency: ").append(String.format("%.2f", metrics.getAverageLatency())).append("ms\n");
        sb.append("Cache Hits: ").append(cache != null ? cache.getHits() : 0).append("\n");
        sb.append("Cache Misses: ").append(cache != null ? cache.getMisses() : 0).append("\n");
        return sb.toString();
    }

    public static void registerEventListener(StorageEvent.EventType type, Consumer<StorageEvent> listener) {
        if (eventEmitter != null) {
            eventEmitter.on(type, listener);
        }
    }

    public static void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    public static void shutdown() {
        scheduler.shutdown();
        if (healthMonitor != null) {
            healthMonitor.stop();
        }
        Logger.info("StorageAPI shutdown complete");
    }

    // ==================== Private Helper Methods ====================

    private static <T> T executeWithRetryAndCircuitBreaker(String serverName, Supplier<T> operation) {
        totalRequests.incrementAndGet();
        try {
            return RetryPolicy.executeWithRetry(() -> {
                ServerConfig targetServer = serverName != null ? findServerByName(serverName) : null;
                if (targetServer == null) {
                    throw new StorageAPIException("Server not found: " + serverName, ErrorCode.SERVER_NOT_FOUND);
                }

                CircuitBreaker cb = healthMonitor.getCircuitBreaker(serverName);
                if (!cb.allowRequest()) {
                    throw new StorageAPIException("Circuit breaker open for server: " + serverName, ErrorCode.CIRCUIT_OPEN);
                }

                try {
                    T result = operation.get();
                    cb.recordSuccess();
                    return result;
                } catch (Exception e) {
                    cb.recordFailure();
                    throw e;
                }
            }, MAX_RETRIES, RETRY_INITIAL_DELAY_MS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String resolveServerUrl(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null) try {
            throw new StorageAPIException("Server not found: " + serverName, ErrorCode.SERVER_NOT_FOUND);
        } catch (StorageAPIException e) {
            throw new RuntimeException(e);
        }
        return server.ip;
    }

    private static ServerConfig findServerByName(String name) {
        for (ServerConfig server : servers.values()) {
            if (server.name.equals(name)) return server;
        }
        return null;
    }

    private static String sendRequest(String url, String serverName) throws IOException {
        long start = System.currentTimeMillis();
        try {
            String response = HttpConnection.sendRequest(url, "GET", getPasswordHashForServer(serverName), null, null, null);
            metrics.recordLatency(System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            metrics.recordFailure();
            try {
                throw new StorageAPIException("Request failed: " + url, ErrorCode.REQUEST_FAILED, e);
            } catch (StorageAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static String sendPostRequest(String url, String body, String serverName) throws IOException {
        long start = System.currentTimeMillis();
        try {
            String response = HttpConnection.sendRequest(url, "POST", getPasswordHashForServer(serverName), body, null, null);
            metrics.recordLatency(System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            metrics.recordFailure();
            try {
                throw new StorageAPIException("POST request failed: " + url, ErrorCode.REQUEST_FAILED, e);
            } catch (StorageAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static String sendDeleteRequest(String url, String serverName) throws IOException {
        long start = System.currentTimeMillis();
        try {
            String response = HttpConnection.sendRequest(url, "DELETE", getPasswordHashForServer(serverName), null, null, null);
            metrics.recordLatency(System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            metrics.recordFailure();
            try {
                throw new StorageAPIException("DELETE request failed: " + url, ErrorCode.REQUEST_FAILED, e);
            } catch (StorageAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static byte[] sendMultipartRequest(String url, byte[] data, String serverName) throws IOException {
        // Simplified multipart - in production use proper multipart encoding
        try {
            return sendBinaryRequest(url, serverName);
        } catch (StorageAPIException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] sendBinaryRequest(String url, String serverName) throws IOException, StorageAPIException {
        long start = System.currentTimeMillis();
        try {
            // Using HttpConnection - would need extended support for binary
            // For now, return dummy
            metrics.recordLatency(System.currentTimeMillis() - start);
            return new byte[0];
        } catch (Exception e) {
            metrics.recordFailure();
            throw new StorageAPIException("Binary request failed: " + url, ErrorCode.REQUEST_FAILED, e);
        }
    }

    private static String getPasswordHashForServer(String serverName) {
        for (ServerConfig server : servers.values()) {
            if (server.name.equals(serverName)) return server.passwordHash;
        }
        return null;
    }

    private static Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        String cleaned = json.replaceAll("[{}\"]", "");
        String[] pairs = cleaned.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private static List<Map<String, Object>> parseJsonArray(String json) {
        // Simplified parser - in production use a proper JSON library
        List<Map<String, Object>> list = new ArrayList<>();
        return list;
    }

    private static Map<String, Object> parseJsonResponse(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null) return map;
        String cleaned = json.replaceAll("[{}\"]", "");
        String[] pairs = cleaned.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    // ==================== Server Management ====================

    public static void addServer(String name, String ip, String passwordHash) {
        int id = servers.size() + 1;
        servers.put(id, new ServerConfig(ip, passwordHash, name));
        propertiesManager.set("api", "serverip-" + id, ip);
        propertiesManager.set("api", "serverpwhash-" + id, passwordHash);
        try {
            propertiesManager.save("api", "storageapi.properties");
        } catch (IOException e) {
            Logger.error("Failed to save properties: " + e.getMessage());
        }
        serverPool.refresh();
    }

    public static void removeServer(String name) {
        servers.values().removeIf(s -> s.name.equals(name));
        serverPool.refresh();
    }

    public static List<String> getServerNames() {
        List<String> names = new ArrayList<>();
        for (ServerConfig server : servers.values()) {
            names.add(server.name);
        }
        return names;
    }

    public static int getServerCount() {
        return servers.size();
    }

    public static int getOnlineServerCount() {
        int count = 0;
        for (ServerConfig server : servers.values()) {
            if (server.online) count++;
        }
        return count;
    }

    // ==================== Batch Operations ====================

    public static BatchResponse executeBatch(BatchRequest batch) {
        BatchResponse response = new BatchResponse();
        for (BatchRequest.BatchOperation op : batch.getOperations()) {
            try {
                Object result = executeBatchOperation(op);
                response.addSuccess(op.getId(), result);
            } catch (Exception e) {
                response.addFailure(op.getId(), e.getMessage());
                if (batch.isStopOnError()) break;
            }
        }
        return response;
    }

    private static Object executeBatchOperation(BatchRequest.BatchOperation op) {
        String server = op.getParams().getOrDefault("server", "default").toString();
        String path = op.getParams().get("path") != null ? op.getParams().get("path").toString() : "";
        return switch (op.getType().toLowerCase()) {
            case "read" -> readFile(server, path);
            case "write" -> writeFile(server, path, op.getParams().get("content").toString());
            case "list" -> listFiles(server, path);
            case "delete" -> deleteFile(server, path);
            case "exists" -> exists(server, path);
            case "create" -> createFile(server, path);
            case "mkdir" -> createDirectory(server, path);
            case "copy" -> copy(server, op.getParams().get("source").toString(), path);
            case "move" -> move(server, op.getParams().get("source").toString(), path);
            case "rename" -> rename(server, op.getParams().get("oldname").toString(), path);
            default -> throw new IllegalArgumentException("Unknown batch operation: " + op.getType());
        };
    }

    public static boolean exists(String serverName, String path) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String server = resolveServerUrl(serverName);
                String url = "http://" + server + "/exists?path=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                return response != null && response.contains("EXISTS");
            } catch (Exception e) {
                metrics.recordFailure();
                try {
                    throw e;
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    // ==================== Server Health Checks ====================

    static void checkServers() {
        for (Map.Entry<Integer, ServerConfig> entry : servers.entrySet()) {
            ServerConfig server = entry.getValue();
            String url = "http://" + server.ip + "/ion";
            boolean wasOnline = server.online;
            server.online = HttpConnection.isOnline(url);
            if (wasOnline != server.online) {
                if (server.online) {
                    eventEmitter.emit(StorageEvent.serverUp(server.name));
                } else {
                    eventEmitter.emit(StorageEvent.serverDown(server.name));
                }
            }
        }
    }

     private static void checkServerNames() {
         for (Map.Entry<Integer, ServerConfig> entry : servers.entrySet()) {
             ServerConfig server = entry.getValue();
             if (server.online) {
                 String url = "http://"+server.ip + "/getname";
                 server.name = HttpConnection.getServerName(url,server.passwordHash);
             }
         }
     }

      public static Map<Integer, ServerConfig> getServers() {
          return Collections.unmodifiableMap(servers);
      }

       // ==================== Exception Classes ====================
        public enum ErrorCode {
            SERVER_NOT_FOUND, REQUEST_FAILED, CIRCUIT_OPEN, TIMEOUT, AUTHENTICATION_FAILED,
            FILE_NOT_FOUND, INVALID_PARAMETERS, SERVER_ERROR, UNKNOWN_ERROR
        }

        public static class StorageAPIException extends Exception {
            private final ErrorCode code;

            public StorageAPIException(String message, ErrorCode code) {
                super(message);
                this.code = code;
            }

            public StorageAPIException(String message, ErrorCode code, Throwable cause) {
                super(message, cause);
                this.code = code;
            }

            public ErrorCode getCode() {
                return code;
            }
        }
    }

