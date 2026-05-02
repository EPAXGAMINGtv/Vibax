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
    private static final long CACHE_TTL_MS = 60000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INITIAL_DELAY_MS = 100;
    private static final double CIRCUIT_BREAKER_THRESHOLD = 0.5;
    private static final int CIRCUIT_BREAKER_MIN_CALLS = 5;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 30000;

    // Statistics
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        InitStorageAPI(true);
        
        // Wait for servers to be checked and have valid status
        String bestServer = null;
        int attempts = 0;
        int maxAttempts = 30; // Wait up to 30 seconds (30 * 1000ms)
        while (bestServer == null && attempts < maxAttempts) {
            try {
                Thread.sleep(1000); // Wait 1 second between checks
                bestServer = getServerWithMostFreeSpace();
                if (bestServer != null) {
                    break;
                }
                Logger.info("Waiting for servers to be online... attempt " + (attempts + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
        
        if (bestServer != null) {
            Logger.info("Server with most free space: " + bestServer);
            boolean mkdirSuccess = StorageAPI.mkdir(bestServer, "lol");
            Logger.info("Mkdir result: " + mkdirSuccess);
        } else {
            Logger.warn("No servers available after waiting");
        }
        
        Logger.info("StorageAPI initialized");
        Logger.info("Total Requests: " + metrics.getTotalRequests());
        Logger.info("Failed Requests: " + metrics.getFailedRequests());
        Logger.info("Average Latency: " + metrics.getAverageLatency() + "ms");
        Logger.info("Cache Hit Rate: " + String.format("%.2f%%", metrics.getCacheHitRate() * 100));
    }

    public static void InitStorageAPI(boolean makeStorageServerOnlineHttpServer) throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Initializing StorageAPI...");

        propertiesManager = new PropertiesManager();
        try {
            propertiesManager.load("api", "storageapi.properties");
        } catch (IOException e) {
            Logger.error("Failed to load properties: " + e.getMessage());
        }

        if (!propertiesManager.exists("api", "serverip-1")) {
            propertiesManager.set("api", "serverip-1", "127.0.0.1:8080");
        }
        if (!propertiesManager.exists("api", "serverpwhash-1")) {
            propertiesManager.set("api", "serverpwhash-1", "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4");
        }
        propertiesManager.save("api", "storageapi.properties");

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

        cache = new CacheLayer(1000, CACHE_TTL_MS);
        eventEmitter = new EventEmitter();
        metrics = new MetricsCollector();
        serverPool = new ServerPool(servers);

        healthMonitor = new HealthMonitor(serverPool, metrics, eventEmitter);
        healthMonitor.start();

        scheduler.scheduleAtFixedRate(() -> {
            checkServers();
            checkServerNames();
        }, 0, 10, TimeUnit.SECONDS);

        if (makeStorageServerOnlineHttpServer) {
            isOnlineServer areStorageServersOnlineServer = new isOnlineServer(1000, 10);
            areStorageServersOnlineServer.addServerHandler(new OnlineHandler(), "/online");
            areStorageServersOnlineServer.startServer();
        }

        Logger.info("StorageAPI initialized in " + (System.currentTimeMillis() - start) + "ms");
    }

    // ==================== NEW FUNCTIONS: Free Space Management ====================

    /**
     * Get free space for a specific server
     * @param serverName Name of the server
     * @return Free space in bytes, or -1 if server not found or offline
     */
    public static long getFreeSpace(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null || !server.online) {
            return -1;
        }
        return server.freeSpace;
    }

    /**
     * Get the server with the most free space
     * @return Name of the server with most free space, or null if no servers available
     */
    public static String getServerWithMostFreeSpace() {
        String bestServer = null;
        long maxFreeSpace = -1;
        for (Map.Entry<Integer, ServerConfig> entry : servers.entrySet()) {
            ServerConfig server = entry.getValue();
            if (server.online && server.freeSpace > maxFreeSpace) {
                maxFreeSpace = server.freeSpace;
                bestServer = server.name;
            }
        }
        return bestServer;
    }

    // Package-private access for other classes in same package
    public static Map<Integer, ServerConfig> getServers() {
        return servers;
    }

    // ==================== Public API Methods ====================

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
                String serverAddress = resolveServerUrl(serverName);
                if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
                String url = "http://" + serverAddress + "/readfile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
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
                }   catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (StorageAPIException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean deleteFile(String serverName, String fileName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String serverAddress = resolveServerUrl(serverName);
                String url = "http://" + serverAddress + "/delete?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
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

    public static boolean exists(String serverName, String path) {
        try {
            String info = readFile(serverName, path);
            return info != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a file exists on the specified server
     * @param serverName Name of the server
     * @param filePath Path to the file
     * @return true if file exists, false otherwise
     */
    public static boolean doesFileExists(String serverName, String filePath) {
        return exists(serverName, filePath);
    }

    /**
     * Check if a directory exists on the specified server
     * @param serverName Name of the server
     * @param dirPath Path to the directory
     * @return true if directory exists, false otherwise
     */
    public static boolean doesDirExists(String serverName, String dirPath) {
        try {
            // Try to list the directory - if it succeeds, it's a directory
            List<String> contents = listFiles(serverName, dirPath);
            return contents != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a directory on the specified server
     * @param serverName Name of the server
     * @param dirPath Path of the directory to create
     * @return true if directory was created successfully, false otherwise
     */
    public static boolean mkdir(String serverName, String dirPath) {
        try {
            String serverAddress = resolveServerUrl(serverName);
            if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
            String url = "http://" + serverAddress + "/createdirectory?path=" + URLEncoder.encode(dirPath, StandardCharsets.UTF_8);
            String response = sendPostRequest(url, "", serverName); // Empty body for directory creation
            return response != null && response.contains("Directory created");
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> listFiles(String serverName, String directory) {
        return listFiles(serverName, directory, false);
    }

    public static List<String> listFiles(String serverName, String directory, boolean recursive) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            String cacheKey = "list:" + serverName + ":" + directory + ":" + recursive;
            if (cache != null) {
                List<String> cached = cache.get(cacheKey, List.class);
                if (cached != null) {
                    metrics.recordCacheHit();
                    return cached;
                }
            }

            String serverAddress = resolveServerUrl(serverName);
            String path = recursive ? "/listfilesrecursive?path=" : "/listfiles?path=";
            String url = "http://" + serverAddress + path + URLEncoder.encode(directory, StandardCharsets.UTF_8);
            String response = null;
            try {
                response = sendRequest(url, serverName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (response == null) return Collections.emptyList();

            List<String> files = new ArrayList<>(Arrays.asList(response.split("\\n")));
            files.removeIf(String::isBlank);

            if (cache != null) {
                cache.put(cacheKey, files, CACHE_TTL_MS);
            }
            metrics.recordSuccess();
            return files;
        });
    }

    // ==================== Server Management ====================

    public static void addServer(String name, String ip, String passwordHash) {
        int id = servers.size() + 1;
        servers.put(id, new ServerConfig(ip, passwordHash, name));
    }

    public static void removeServer(String name) {
        servers.values().removeIf(server -> server.name.equals(name));
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

    // ==================== Monitoring ====================

    public static Map<String, Object> getServerHealth(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null) return Map.of("error", "Server not found");
        Map<String, Object> health = new HashMap<>();
        health.put("name", server.name);
        health.put("online", server.online);
        health.put("url", server.ip);
        health.put("freeSpace", server.freeSpace);
        health.put("totalSpace", server.totalSpace);
        health.put("cpuUsage", server.cpuUsage);
        health.put("memoryUsed", server.memoryUsed);
        health.put("memoryTotal", server.memoryTotal);
        health.put("latency", healthMonitor != null ? healthMonitor.getLatency(serverName) : 0);
        health.put("uptime", healthMonitor != null ? healthMonitor.getUptime(serverName) : 0);
        health.put("status", healthMonitor != null ? healthMonitor.getStatus(serverName).name() : "UNKNOWN");
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
        sb.append("Online: ").append(getOnlineServerCount()).append("\n");
        sb.append("Total Requests: ").append(metrics.getTotalRequests()).append("\n");
        sb.append("Failed: ").append(metrics.getFailedRequests()).append("\n");
        sb.append("Avg Latency: ").append(String.format("%.2f", metrics.getAverageLatency())).append("ms\n");
        sb.append("Cache Hits: ").append(cache != null ? cache.getHits() : 0).append("\n");
        sb.append("Cache Misses: ").append(cache != null ? cache.getMisses() : 0).append("\n");
        return sb.toString();
    }

    public static void registerEventListener(StorageEvent.EventType type, Consumer<StorageEvent> listener) {
        if (eventEmitter != null) eventEmitter.on(type, listener);
    }

    public static void clearCache() {
        if (cache != null) cache.clear();
    }

    public static void shutdown() {
        scheduler.shutdown();
        if (healthMonitor != null) healthMonitor.stop();
        Logger.info("StorageAPI shutdown complete");
    }

    // ==================== Retry & Circuit Breaker Support ====================

    private static <T> T executeWithRetryAndCircuitBreaker(String serverName, Supplier<T> operation) {
        totalRequests.incrementAndGet();
        try {
            ServerConfig targetServer = findServerByName(serverName);
            if (targetServer == null) {
                throw new StorageAPIException("Server not found: " + serverName, ErrorCode.SERVER_NOT_FOUND);
            }

            CircuitBreaker cb = healthMonitor != null ? healthMonitor.getCircuitBreaker(serverName) : null;
            if (cb != null && !cb.allowRequest()) {
                throw new StorageAPIException("Circuit breaker open for server: " + serverName, ErrorCode.CIRCUIT_OPEN);
            }

            try {
                T result = RetryPolicy.executeWithRetry((Callable<T>) operation, MAX_RETRIES, RETRY_INITIAL_DELAY_MS);
                if (cb != null) cb.recordSuccess();
                metrics.recordSuccess();
                return result;
            } catch (Exception e) {
                if (cb != null) cb.recordFailure();
                metrics.recordFailure();
                throw e;
            }
        } catch (Exception e) {
            if (e instanceof StorageAPIException) {
                try {
                    throw (StorageAPIException) e;
                } catch (StorageAPIException ex) {
                    throw new RuntimeException(ex);
                }
            }
            try {
                throw new StorageAPIException("Request failed", ErrorCode.REQUEST_FAILED, e);
            } catch (StorageAPIException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @FunctionalInterface
    private interface VoidSupplier {
        void run() throws Exception;
    }

    private static void executeWithRetryAndCircuitBreakerVoid(String serverName, VoidSupplier operation) {
        executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                operation.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    public static boolean writeFile(String serverName, String fileName, String content) {
        try {
            executeWithRetryAndCircuitBreakerVoid(serverName, () -> {
                String serverAddress = resolveServerUrl(serverName);
                if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
                String url = "http://" + serverAddress + "/writefile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, content, serverName);
                cache.invalidate("read:" + serverName + ":" + fileName);
                if (response == null || !response.contains("Written to")) {
                    throw new StorageAPIException("Write failed", ErrorCode.REQUEST_FAILED);
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static BatchResponse executeBatch(BatchRequest batch) {
        BatchResponse response = new BatchResponse();
        for (BatchRequest.BatchOperation operation : batch.getOperations()) {
            try {
                boolean success = false;
                String result = "";
                switch (operation.getType()) {
                    case "write":
                        success = writeFile(
                            (String) operation.getParams().get("server"),
                            (String) operation.getParams().get("path"),
                            (String) operation.getParams().get("content")
                        );
                        result = success ? "File written" : "Write failed";
                        break;
                    case "read":
                        String content = readFile(
                            (String) operation.getParams().get("server"),
                            (String) operation.getParams().get("path")
                        );
                        success = content != null;
                        result = success ? "File read" : "Read failed";
                        break;
                    case "delete":
                        success = deleteFile(
                            (String) operation.getParams().get("server"),
                            (String) operation.getParams().get("path")
                        );
                        result = success ? "File deleted" : "Delete failed";
                        break;
                    case "exists":
                        success = exists(
                            (String) operation.getParams().get("server"),
                            (String) operation.getParams().get("path")
                        );
                        result = success ? "Exists" : "Not found";
                        break;
                    default:
                        result = "Unknown operation: " + operation.getType();
                }
                if (success) response.addSuccess(operation.getId(), result);
                else response.addFailure(operation.getId(), result);
            } catch (Exception e) {
                response.addFailure(operation.getId(), "Error: " + e.getMessage());
            }
        }
        return response;
    }

    // ==================== Private Helper Methods ====================

    private static ServerConfig findServerByName(String serverName) {
        for (ServerConfig server : servers.values()) {
            if (server.name.equals(serverName)) return server;
        }
        return null;
    }

    private static String resolveServerUrl(String serverName) {
        ServerConfig server = findServerByName(serverName);
        return server != null ? server.ip : null;
    }

    private static String sendRequest(String url, String serverName) throws IOException {
        ServerConfig server = findServerByName(serverName);
        try {
            return server != null ? HttpConnection.sendRequest(url, "GET", server.passwordHash, null, null, null) : null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String sendPostRequest(String url, String content, String serverName) throws IOException {
        ServerConfig server = findServerByName(serverName);
        try {
            return server != null ? HttpConnection.sendRequest(url, "POST", server.passwordHash, content, null, null) : null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String sendDeleteRequest(String url, String serverName) throws IOException {
        ServerConfig server = findServerByName(serverName);
        try {
            return server != null ? HttpConnection.sendRequest(url, "DELETE", server.passwordHash, null, null, null) : null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> parseJsonResponse(String json) { return new HashMap<>(); }
    private static List<Map<String, Object>> parseJsonArray(String json) { return new ArrayList<>(); }
    private static Map<String, String> parseJsonToMap(String json) { return new HashMap<>(); }

    static void checkServers() {
        if (healthMonitor != null) {
            // Health checks handled by HealthMonitor
        }
    }

    static void checkServerNames() {
        for (Map.Entry<Integer, ServerConfig> entry : servers.entrySet()) {
            ServerConfig server = entry.getValue();
            if (server.online) {
                String url = "http://" + server.ip + "/getname";
                server.name = HttpConnection.getServerName(url, server.passwordHash);
            }
        }
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
        public ErrorCode getCode() { return code; }
    }
}
