package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PartialReadHandler extends AuthenticatedHandler implements HttpHandler {

    public PartialReadHandler(String passwordHash) {
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
        String offsetStr = params.get("offset");
        String lengthStr = params.get("length");

        if (path == null) {
            sendText(exchange, 400, "Required parameter: path");
            return;
        }

        try {
            long offset = offsetStr != null ? Long.parseLong(offsetStr) : 0;
            long length = lengthStr != null ? Long.parseLong(lengthStr) : -1;

            byte[] data = FileManager.readPartialFile(path, offset, length);
            String base64 = Base64.getEncoder().encodeToString(data);

            Map<String, Object> result = new ConcurrentHashMap<>();
            result.put("path", path);
            result.put("offset", offset);
            result.put("length", data.length);
            result.put("data", base64);
            result.put("eof", length == -1 || data.length < length);

            sendJson(exchange, 200, toJson(result));
        } catch (Exception e) {
            sendText(exchange, 500, "Partial read failed: " + e.getMessage());
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

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            Object value = e.getValue();
            if (value instanceof String) {
                sb.append("\"").append(e.getKey()).append("\":\"")
                  .append(((String) value).replace("\"", "\\\"")).append("\"");
            } else {
                sb.append("\"").append(e.getKey()).append("\":").append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
