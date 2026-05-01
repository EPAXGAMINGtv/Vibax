package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class JsonHandler extends AuthenticatedHandler {

    public JsonHandler(String passwordHash) {
        super(passwordHash);
    }

    protected void sendJson(HttpExchange exchange, int code, Map<String, ?> map) throws IOException {
        String json = mapToJson(map);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected String mapToJson(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(",");
            Object val = e.getValue();
            if (val instanceof String) {
                sb.append("\"").append(e.getKey()).append("\":\"")
                  .append(((String) val).replace("\"", "\\\"")).append("\"");
            } else if (val instanceof Number) {
                sb.append("\"").append(e.getKey()).append("\":").append(val);
            } else if (val instanceof Boolean) {
                sb.append("\"").append(e.getKey()).append("\":").append(val);
            } else if (val == null) {
                sb.append("\"").append(e.getKey()).append("\":null");
            } else {
                sb.append("\"").append(e.getKey()).append("\":\"")
                  .append(val.toString().replace("\"", "\\\"")).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
