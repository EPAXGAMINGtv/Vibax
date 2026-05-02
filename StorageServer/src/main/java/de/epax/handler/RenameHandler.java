package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECURITY FIX:
 *  - Validate newName: reject path separators and ".." to prevent path traversal
 *  - Reject null/blank newName
 */
public class RenameHandler extends AuthenticatedHandler implements HttpHandler {

    public RenameHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Only POST allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();
        if (query == null) {
            sendText(exchange, 400, "Missing query parameters");
            return;
        }

        Map<String, String> params = parseQuery(query);
        String path    = params.get("path");
        String newName = params.get("newname");

        if (path == null || newName == null) {
            sendText(exchange, 400, "Required parameters: path, newname");
            return;
        }

        // SECURITY FIX: Prevent path traversal via newName
        if (newName.isBlank() || newName.contains("/") || newName.contains("\\") || newName.contains("..")) {
            sendText(exchange, 400, "Invalid newname: must not contain path separators or '..'");
            return;
        }

        try {
            FileManager.rename(path, newName);
            sendText(exchange, 200, "Renamed: " + path + " -> " + newName);
        } catch (Exception e) {
            sendText(exchange, 500, "Rename failed: " + e.getMessage());
        }
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
