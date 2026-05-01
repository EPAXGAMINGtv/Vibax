package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChecksumHandler extends JsonHandler implements HttpHandler {

    public ChecksumHandler(String passwordHash) {
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
            sendText(exchange, 400, "Missing ?path= parameter");
            return;
        }

        Map<String, String> params = parseQuery(query);
        String path = params.get("path");
        String algorithm = params.getOrDefault("algorithm", "SHA-256");

        if (path == null) {
            sendText(exchange, 400, "Required parameter: path");
            return;
        }

        try {
            String checksum;
            if (algorithm.equalsIgnoreCase("crc32")) {
                checksum = FileManager.calculateCRC32(path);
            } else {
                checksum = FileManager.calculateChecksum(path, algorithm);
            }
            Map<String, String> result = new ConcurrentHashMap<>();
            result.put("path", path);
            result.put("algorithm", algorithm);
            result.put("checksum", checksum);
            sendJson(exchange, 200, result);
        } catch (Exception e) {
            sendText(exchange, 500, "Checksum calculation failed: " + e.getMessage());
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new ConcurrentHashMap<>();
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
