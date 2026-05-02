package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * SECURITY FIX:
 *  - Removed storagePath from all responses (information disclosure)
 *  - Fixed GZip Content-Length bug (sendResponseHeaders(200, 0) for streaming gzip)
 */
public class AdminHandler extends JsonHandler implements HttpHandler {
    private final String serverName;
    private final int port;
    private final int maxConnections;
    private final String storagePath; // kept for internal use only, never sent to client

    public AdminHandler(String passwordHash, String serverName, int port, int maxConnections, String storagePath) {
        super(passwordHash);
        this.serverName     = serverName;
        this.port           = port;
        this.maxConnections = maxConnections;
        this.storagePath    = storagePath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();

        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, uri);
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, uri);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange, uri);
            } else {
                sendText(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", "Admin operation failed", "message", e.getMessage()));
        }
    }

    private void handleGet(HttpExchange exchange, URI uri) throws IOException {
        String query = uri.getQuery();
        if (query == null) {
            sendJson(exchange, 400, Map.of("error", "Missing action parameter"));
            return;
        }
        Map<String, String> params = parseQuery(query);
        String action = params.get("action");
        if (action == null) {
            sendJson(exchange, 400, Map.of("error", "Missing action"));
            return;
        }
        switch (action) {
            case "stats"  -> sendStats(exchange);
            case "config" -> sendConfig(exchange);
            case "logs"   -> sendLogs(exchange, params);
            case "health" -> sendHealth(exchange);
            default       -> sendJson(exchange, 400, Map.of("error", "Unknown action: " + action));
        }
    }

    private void handlePost(HttpExchange exchange, URI uri) throws IOException {
        String query = uri.getQuery();
        if (query == null) {
            sendJson(exchange, 400, Map.of("error", "Missing action parameter"));
            return;
        }
        Map<String, String> params = parseQuery(query);
        if ("logs".equals(params.get("action"))) {
            sendJson(exchange, 200, Map.of("result", "logs cleared"));
        } else {
            sendJson(exchange, 400, Map.of("error", "Unknown post action"));
        }
    }

    private void handleDelete(HttpExchange exchange, URI uri) throws IOException {
        String query = uri.getQuery();
        if (query != null) {
            Map<String, String> params = parseQuery(query);
            if ("cache".equals(params.get("action"))) {
                FileManager.invalidateCache();
                sendJson(exchange, 200, Map.of("result", "cache cleared"));
                return;
            }
        }
        sendJson(exchange, 400, Map.of("error", "Invalid delete action"));
    }

    private void sendStats(HttpExchange exchange) throws IOException {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        // SECURITY FIX: storagePath removed from response
        stats.put("serverName", serverName);
        stats.put("maxConnections", maxConnections);
        try {
            java.io.File root = new java.io.File(storagePath);
            stats.put("totalSizeBytes", root.exists() ? calculateSize(root) : 0);
            stats.put("fileCount",      root.exists() ? countFiles(root)    : 0);
        } catch (Exception e) {
            stats.put("sizeError", e.getMessage());
        }
        sendJson(exchange, 200, stats);
    }

    private void sendConfig(HttpExchange exchange) throws IOException {
        Map<String, Object> config = new ConcurrentHashMap<>();
        // SECURITY FIX: storagePath removed from response
        config.put("serverName", serverName);
        config.put("port",       port);
        sendJson(exchange, 200, config);
    }

    private void sendLogs(HttpExchange exchange, Map<String, String> params) throws IOException {
        String linesParam = params.get("lines");
        int lines = 100;
        if (linesParam != null) {
            try { lines = Integer.parseInt(linesParam); } catch (Exception ignored) {}
        }

        StringBuilder log = new StringBuilder();
        log.append("=== Recent Log Entries ===\n");
        log.append("Log streaming not yet implemented.\n");

        byte[] bytes = log.toString().getBytes(StandardCharsets.UTF_8);

        if (params.containsKey("gzip")) {
            // SECURITY FIX: Use 0 (chunked) for gzip — not bytes.length (which is uncompressed)
            exchange.getResponseHeaders().set("Content-Type",     "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, 0);
            try (GZIPOutputStream gzip = new GZIPOutputStream(exchange.getResponseBody())) {
                gzip.write(bytes);
            }
        } else {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendHealth(HttpExchange exchange) throws IOException {
        Map<String, Object> health = new ConcurrentHashMap<>();
        // SECURITY FIX: storagePath removed from response
        health.put("status",    "healthy");
        health.put("timestamp", java.time.LocalDateTime.now().toString());
        health.put("uptime",    ManagementFactory.getRuntimeMXBean().getUptime() + "ms");
        sendJson(exchange, 200, health);
    }

    private long calculateSize(java.io.File dir) {
        long size = 0;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) size += f.length();
                else if (f.isDirectory()) size += calculateSize(f);
            }
        }
        return size;
    }

    private long countFiles(java.io.File dir) {
        long count = 0;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) count++;
                else if (f.isDirectory()) count += countFiles(f);
            }
        }
        return count;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                result.put(decodeURL(pair.substring(0, idx)), decodeURL(pair.substring(idx + 1)));
            }
        }
        return result;
    }

    private String decodeURL(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
