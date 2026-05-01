package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VersionListHandler extends JsonHandler implements HttpHandler {

    public VersionListHandler(String passwordHash) {
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
        if (query == null) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            List<Map<String, Object>> versions = FileManager.getVersions(path);
            Map<String, Object> result = new ConcurrentHashMap<>();
            result.put("versions", versions);
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            sendText(exchange, 500, "Version listing failed: " + e.getMessage());
        }
    }
}
