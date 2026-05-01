package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LockStatusHandler extends JsonHandler implements HttpHandler {

    public LockStatusHandler(String passwordHash) {
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
            boolean locked = FileManager.isLocked(path);
            Map<String, Object> result = new ConcurrentHashMap<>();
            result.put("path", path);
            result.put("locked", locked);
            if (locked) {
                result.put("owner", FileManager.getLockOwner(path));
            }
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            sendText(exchange, 500, "Lock status check failed: " + e.getMessage());
        }
    }
}
