package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.StorageServerStart;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InfoHandler extends JsonHandler implements HttpHandler {

    public InfoHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendText(exchange, 405, "Only GET allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();
        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            String info = FileManager.getFileInfo(path);
            
            // Get disk space information
            java.io.File root = new java.io.File(StorageServerStart.getStoragePath());
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            
            Map<String, Object> result = new ConcurrentHashMap<>();
            result.put("info", info);
            result.put("freeSpace", freeSpace);
            result.put("totalSpace", totalSpace);
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            sendText(exchange, 500, "Info retrieval failed: " + e.getMessage());
        }
    }
}
