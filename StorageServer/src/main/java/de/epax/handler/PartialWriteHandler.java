package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PartialWriteHandler extends AuthenticatedHandler implements HttpHandler {

    public PartialWriteHandler(String passwordHash) {
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
        String path = params.get("path");
        String offsetStr = params.get("offset");

        if (path == null || offsetStr == null) {
            sendText(exchange, 400, "Required parameters: path, offset");
            return;
        }

        try {
            long offset = Long.parseLong(offsetStr);
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> json = parseJson(requestBody);
            String dataBase64 = (String) json.get("data");
            if (dataBase64 == null) {
                sendText(exchange, 400, "Missing 'data' field in request body");
                return;
            }
            byte[] data = Base64.getDecoder().decode(dataBase64);
            FileManager.writePartialFile(path, offset, data);
            sendText(exchange, 200, "Written " + data.length + " bytes at offset " + offset);
        } catch (Exception e) {
            sendText(exchange, 500, "Partial write failed: " + e.getMessage());
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

    private Map<String, Object> parseJson(String json) throws IOException {
        Map<String, Object> result = new ConcurrentHashMap<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = inner.split(",");
            for (String part : parts) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].replace("\"", "").trim();
                    String value = kv[1].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1).replace("\\\"", "\"");
                    } else if (value.equals("true") || value.equals("false")) {
                        result.put(key, Boolean.parseBoolean(value));
                    } else if (value.matches("-?\\d+")) {
                        result.put(key, Integer.parseInt(value));
                    } else if (value.matches("-?\\d+\\.\\d+")) {
                        result.put(key, Double.parseDouble(value));
                    } else {
                        result.put(key, value);
                    }
                }
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
