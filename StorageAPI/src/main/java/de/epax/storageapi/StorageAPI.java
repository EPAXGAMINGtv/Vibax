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

/**
 * SECURITY FIXES:
 *  - Removed hardcoded default passwordHash "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
 *    (was SHA-256 of "1234"). storageapi.properties now requires the property to be set manually.
 *  - storageapi.properties no longer auto-creates insecure defaults on startup.
 */
public class StorageAPI {

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

    private static final boolean ENABLE_CACHE                = true;
    private static final long    CACHE_TTL_MS                = 60_000L;
    private static final int     MAX_RETRIES                 = 3;
    private static final long    RETRY_INITIAL_DELAY_MS      = 100L;
    private static final double  CIRCUIT_BREAKER_THRESHOLD   = 0.5;
    private static final int     CIRCUIT_BREAKER_MIN_CALLS   = 5;
    private static final long    CIRCUIT_BREAKER_TIMEOUT_MS  = 30_000L;

    private static final AtomicInteger totalRequests  = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        InitStorageAPI(true);

        String bestServer = null;
        int attempts = 0;
        while (bestServer == null && attempts < 30) {
            try {
                Thread.sleep(1000);
                bestServer = getServerWithMostFreeSpace();
                if (bestServer != null) break;
                Logger.info("Waiting for servers... attempt " + (attempts + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }

        if (bestServer != null) {
            Logger.info("Server with most free space: " + bestServer);
            Logger.info("Mkdir result: " + StorageAPI.mkdir(bestServer, "lol"));
        } else {
            Logger.warn("No servers available after waiting");
        }

        Logger.info("StorageAPI initialized");
        Logger.info("Total Requests: "    + metrics.getTotalRequests());
        Logger.info("Failed Requests: "   + metrics.getFailedRequests());
        Logger.info("Average Latency: "   + metrics.getAverageLatency() + "ms");
        Logger.info("Cache Hit Rate: "    + String.format("%.2f%%", metrics.getCacheHitRate() * 100));
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

        // SECURITY FIX: No default insecure values. Require explicit configuration.
        if (!propertiesManager.exists("api", "serverip-1")) {
            propertiesManager.set("api", "serverip-1", "127.0.0.1:8000");
            Logger.warn("storageapi.properties: set serverpwhash-1 to your actual API password!");
        }
        if (!propertiesManager.exists("api", "serverpwhash-1")) {
            // SECURITY FIX: Do NOT write a default hash. Fail loudly instead.
            Logger.error("storageapi.properties: 'serverpwhash-1' is not set! Set it to your StorageServer API password.");
            // Leave the entry absent so the server won't authenticate until the admin sets it.
        }

        try {
            propertiesManager.save("api", "storageapi.properties");
        } catch (IOException e) {
            Logger.error("Failed to save properties: " + e.getMessage());
        }

        Properties props = propertiesManager.getProperties("api");
        if (props != null) {
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("serverip-")) {
                    int id      = Integer.parseInt(key.split("-")[1]);
                    String ip   = props.getProperty(key);
                    String hash = props.getProperty("serverpwhash-" + id);
                    if (hash != null && !hash.isBlank()) {
                        servers.put(id, new ServerConfig(ip, hash));
                    } else {
                        Logger.warn("Server " + id + " skipped — no password hash configured.");
                    }
                }
            }
        }

        cache        = new CacheLayer(1000, CACHE_TTL_MS);
        eventEmitter = new EventEmitter();
        metrics      = new MetricsCollector();
        serverPool   = new ServerPool(servers);
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

    // ==================== Free Space Management ====================

    public static long getFreeSpace(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null || !server.online) return -1;
        return server.freeSpace;
    }

    public static String getServerWithMostFreeSpace() {
        String bestServer = null;
        long maxFreeSpace = -1;
        for (ServerConfig server : servers.values()) {
            if (server.online && server.freeSpace > maxFreeSpace) {
                maxFreeSpace = server.freeSpace;
                bestServer   = server.name;
            }
        }
        return bestServer;
    }

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
            if (cached != null) { metrics.recordCacheHit(); return cached; }
        }

        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String serverAddress = resolveServerUrl(serverName);
                if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
                String url      = "http://" + serverAddress + "/readfile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendRequest(url, serverName);
                if (useCache && cache != null && response != null) cache.put(cacheKey, response, CACHE_TTL_MS);
                metrics.recordSuccess();
                return response;
            } catch (Exception e) {
                metrics.recordFailure();
                if (e instanceof IOException)          throw new RuntimeException(e);
                if (e instanceof StorageAPIException)  throw new RuntimeException(e);
                try {
                    throw e;
                } catch (StorageAPIException ex) {
                    throw new RuntimeException(ex);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public static boolean deleteFile(String serverName, String fileName) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            try {
                String serverAddress = resolveServerUrl(serverName);
                String url           = "http://" + serverAddress + "/delete?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response      = sendDeleteRequest(url, serverName);
                cache.invalidate("read:" + serverName + ":" + fileName);
                cache.invalidate("list:" + serverName + ":" + (new File(fileName).getParent()));
                metrics.recordSuccess();
                return response != null && response.contains("Deleted");
            } catch (Exception e) {
                metrics.recordFailure();
                if (e instanceof IOException) throw new RuntimeException(e);
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
            return readFile(serverName, path) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean doesFileExists(String serverName, String filePath) { return exists(serverName, filePath); }
    public static boolean doesDirExists(String serverName, String dirPath) {
        try {
            List<String> contents = listFiles(serverName, dirPath);
            return contents != null;
        } catch (Exception e) { return false; }
    }

    public static boolean mkdir(String serverName, String dirPath) {
        try {
            String serverAddress = resolveServerUrl(serverName);
            if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
            String url      = "http://" + serverAddress + "/createdirectory?path=" + URLEncoder.encode(dirPath, StandardCharsets.UTF_8);
            String response = sendPostRequest(url, "", serverName);
            return response != null && response.contains("Directory created");
        } catch (Exception e) { return false; }
    }

    public static List<String> listFiles(String serverName, String directory) {
        return listFiles(serverName, directory, false);
    }

    public static List<String> listFiles(String serverName, String directory, boolean recursive) {
        return executeWithRetryAndCircuitBreaker(serverName, () -> {
            String cacheKey = "list:" + serverName + ":" + directory + ":" + recursive;
            if (cache != null) {
                List<String> cached = cache.get(cacheKey, List.class);
                if (cached != null) { metrics.recordCacheHit(); return cached; }
            }
            String serverAddress = resolveServerUrl(serverName);
            String path          = recursive ? "/listfilesrecursive?path=" : "/listfiles?path=";
            String url           = "http://" + serverAddress + path + URLEncoder.encode(directory, StandardCharsets.UTF_8);
            String response;
            try { response = sendRequest(url, serverName); }
            catch (IOException e) { throw new RuntimeException(e); }
            if (response == null) return Collections.emptyList();
            List<String> files = new ArrayList<>(Arrays.asList(response.split("\n")));
            files.removeIf(String::isBlank);
            if (cache != null) cache.put(cacheKey, files, CACHE_TTL_MS);
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
        servers.values().removeIf(s -> s.name.equals(name));
    }

    public static List<String> getServerNames() {
        List<String> names = new ArrayList<>();
        for (ServerConfig s : servers.values()) names.add(s.name);
        return names;
    }

    public static int getServerCount()       { return servers.size(); }
    public static int getOnlineServerCount() {
        return (int) servers.values().stream().filter(s -> s.online).count();
    }

    // ==================== Monitoring ====================

    public static Map<String, Object> getServerHealth(String serverName) {
        ServerConfig server = findServerByName(serverName);
        if (server == null) return Map.of("error", "Server not found");
        Map<String, Object> health = new HashMap<>();
        health.put("name",        server.name);
        health.put("online",      server.online);
        health.put("url",         server.ip);
        health.put("freeSpace",   server.freeSpace);
        health.put("totalSpace",  server.totalSpace);
        health.put("cpuUsage",    server.cpuUsage);
        health.put("memoryUsed",  server.memoryUsed);
        health.put("memoryTotal", server.memoryTotal);
        health.put("latency",  healthMonitor != null ? healthMonitor.getLatency(serverName)  : 0);
        health.put("uptime",   healthMonitor != null ? healthMonitor.getUptime(serverName)   : 0);
        health.put("status",   healthMonitor != null ? healthMonitor.getStatus(serverName).name() : "UNKNOWN");
        return health;
    }

    public static Map<String, Object> getAllServerHealth() {
        Map<String, Object> all = new HashMap<>();
        for (ServerConfig s : servers.values()) all.put(s.name, getServerHealth(s.name));
        return all;
    }

    public static Map<String, Object> getMetrics() {
        return Map.of(
            "totalRequests",    metrics.getTotalRequests(),
            "failedRequests",   metrics.getFailedRequests(),
            "averageLatencyMs", metrics.getAverageLatency(),
            "requestsPerSecond",metrics.getRequestsPerSecond(),
            "errorRate",        metrics.getErrorRate(),
            "cacheHitRate",     cache != null ? cache.getHitRate() : 0.0
        );
    }

    public static String getStats() {
        return "StorageAPI Statistics\n" +
               "=====================\n" +
               "Servers: "       + servers.size()                                              + "\n" +
               "Online: "        + getOnlineServerCount()                                      + "\n" +
               "Total Requests: "+ metrics.getTotalRequests()                                  + "\n" +
               "Failed: "        + metrics.getFailedRequests()                                 + "\n" +
               "Avg Latency: "   + String.format("%.2f", metrics.getAverageLatency()) + "ms\n" +
               "Cache Hits: "    + (cache != null ? cache.getHits()   : 0)                    + "\n" +
               "Cache Misses: "  + (cache != null ? cache.getMisses() : 0)                    + "\n";
    }

    public static void registerEventListener(StorageEvent.EventType type, Consumer<StorageEvent> listener) {
        if (eventEmitter != null) eventEmitter.on(type, listener);
    }

    public static void clearCache()  { if (cache != null) cache.clear(); }

    public static void shutdown() {
        scheduler.shutdown();
        if (healthMonitor != null) healthMonitor.stop();
        Logger.info("StorageAPI shutdown complete");
    }

    // ==================== Retry & Circuit Breaker ====================

    private static <T> T executeWithRetryAndCircuitBreaker(String serverName, Supplier<T> operation) {
        totalRequests.incrementAndGet();
        try {
            ServerConfig targetServer = findServerByName(serverName);
            if (targetServer == null)
                throw new StorageAPIException("Server not found: " + serverName, ErrorCode.SERVER_NOT_FOUND);

            CircuitBreaker cb = healthMonitor != null ? healthMonitor.getCircuitBreaker(serverName) : null;
            if (cb != null && !cb.allowRequest())
                throw new StorageAPIException("Circuit breaker open for: " + serverName, ErrorCode.CIRCUIT_OPEN);

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
        } catch (StorageAPIException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(new StorageAPIException("Request failed", ErrorCode.REQUEST_FAILED, e));
        }
    }

    @FunctionalInterface
    private interface VoidSupplier { void run() throws Exception; }

    private static void executeWithRetryAndCircuitBreakerVoid(String serverName, VoidSupplier op) {
        executeWithRetryAndCircuitBreaker(serverName, () -> {
            try { op.run(); } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    public static boolean writeFile(String serverName, String fileName, String content) {
        try {
            executeWithRetryAndCircuitBreakerVoid(serverName, () -> {
                String serverAddress = resolveServerUrl(serverName);
                if (serverAddress == null) throw new StorageAPIException("Server not found", ErrorCode.SERVER_NOT_FOUND);
                String url      = "http://" + serverAddress + "/writefile?path=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String response = sendPostRequest(url, content, serverName);
                cache.invalidate("read:" + serverName + ":" + fileName);
                if (response == null || !response.contains("Written to"))
                    throw new StorageAPIException("Write failed", ErrorCode.REQUEST_FAILED);
            });
            return true;
        } catch (Exception e) { return false; }
    }

    public static BatchResponse executeBatch(BatchRequest batch) {
        BatchResponse response = new BatchResponse();
        for (BatchRequest.BatchOperation op : batch.getOperations()) {
            try {
                boolean success = false;
                String  result  = "";
                switch (op.getType()) {
                    case "write"  -> { success = writeFile((String)op.getParams().get("server"),   (String)op.getParams().get("path"), (String)op.getParams().get("content")); result = success ? "File written"  : "Write failed";  }
                    case "read"   -> { String c = readFile((String)op.getParams().get("server"),   (String)op.getParams().get("path")); success = c != null; result = success ? "File read"     : "Read failed";   }
                    case "delete" -> { success = deleteFile((String)op.getParams().get("server"),  (String)op.getParams().get("path")); result = success ? "File deleted"  : "Delete failed"; }
                    case "exists" -> { success = exists((String)op.getParams().get("server"),      (String)op.getParams().get("path")); result = success ? "Exists"        : "Not found";     }
                    default       ->   result  = "Unknown operation: " + op.getType();
                }
                if (success) response.addSuccess(op.getId(), result);
                else         response.addFailure(op.getId(), result);
            } catch (Exception e) {
                response.addFailure(op.getId(), "Error: " + e.getMessage());
            }
        }
        return response;
    }

    // ==================== Private Helpers ====================

    private static ServerConfig findServerByName(String serverName) {
        for (ServerConfig s : servers.values()) if (s.name.equals(serverName)) return s;
        return null;
    }

    private static String resolveServerUrl(String serverName) {
        ServerConfig s = findServerByName(serverName);
        return s != null ? s.ip : null;
    }

    private static String sendRequest(String url, String serverName) throws IOException {
        ServerConfig s = findServerByName(serverName);
        try { return s != null ? HttpConnection.sendRequest(url, "GET",    s.passwordHash, null, null, null) : null; }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static String sendPostRequest(String url, String content, String serverName) throws IOException {
        ServerConfig s = findServerByName(serverName);
        try { return s != null ? HttpConnection.sendRequest(url, "POST",   s.passwordHash, content, null, null) : null; }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    private static String sendDeleteRequest(String url, String serverName) throws IOException {
        ServerConfig s = findServerByName(serverName);
        try { return s != null ? HttpConnection.sendRequest(url, "DELETE", s.passwordHash, null, null, null) : null; }
        catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    static void checkServers()     { /* Health checks handled by HealthMonitor */ }
    static void checkServerNames() {
        for (ServerConfig server : servers.values()) {
            if (server.online) {
                server.name = HttpConnection.getServerName("http://" + server.ip + "/getname", server.passwordHash);
            }
        }
    }

    // ==================== Exceptions ====================

    public enum ErrorCode {
        SERVER_NOT_FOUND, REQUEST_FAILED, CIRCUIT_OPEN, TIMEOUT,
        AUTHENTICATION_FAILED, FILE_NOT_FOUND, INVALID_PARAMETERS, SERVER_ERROR, UNKNOWN_ERROR
    }

    public static class StorageAPIException extends Exception {
        private final ErrorCode code;
        public StorageAPIException(String message, ErrorCode code)                { super(message); this.code = code; }
        public StorageAPIException(String message, ErrorCode code, Throwable cause) { super(message, cause); this.code = code; }
        public ErrorCode getCode() { return code; }
    }
}
