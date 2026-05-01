package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map;

public class GetMetadataHandler extends JsonHandler implements HttpHandler {

    public GetMetadataHandler(String passwordHash) {
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
            sendText(exchange, 400, "Missing query parameters");
            return;
        }

        Map<String, String> params = parseQuery(query);
        String path = params.get("path");
        String key = params.get("key");

        if (path == null || key == null) {
            sendText(exchange, 400, "Required parameters: path, key");
            return;
        }

        try {
            String value = FileManager.getMetadata(path, key);
            if (value == null) {
                sendText(exchange, 404, "Metadata key not found");
                return;
            }
            Map<String, String> result = new HashMap<>();
            result.put("path", path);
            result.put(key, value);
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            sendText(exchange, 500, "Get metadata failed: " + e.getMessage());
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new java.util.concurrent.ConcurrentHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = decodeURL(pair.substring(0, idx));
                String value = decodeURL(pair.substring(idx + 1));
                result.put(key, value);
            }
        }
        return result;
    }

    private String decodeURL(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
