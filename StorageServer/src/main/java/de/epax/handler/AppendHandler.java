package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppendHandler extends AuthenticatedHandler implements HttpHandler {

    public AppendHandler(String passwordHash) {
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
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        try {
            FileManager.appendToFile(path, body);
            sendText(exchange, 200, "Appended to " + path);
        } catch (Exception e) {
            sendText(exchange, 500, "Append failed: " + e.getMessage());
        }
    }
}
