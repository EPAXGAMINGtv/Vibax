package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoveDirectoryHandler extends AuthenticatedHandler implements HttpHandler {

    public MoveDirectoryHandler(String passwordHash) {
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
        String source = params.get("source");
        String target = params.get("target");

        if (source == null || target == null) {
            sendText(exchange, 400, "Required parameters: source, target");
            return;
        }

        try {
            FileManager.moveDirectory(source, target);
            sendText(exchange, 200, "Directory moved: " + source + " -> " + target);
        } catch (Exception e) {
            sendText(exchange, 500, "Directory move failed: " + e.getMessage());
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
