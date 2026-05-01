package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;

public class ExistsHandler extends AuthenticatedHandler implements HttpHandler {

    public ExistsHandler(String passwordHash) {
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

        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            boolean exists = FileManager.exists(path);
            String response = exists ? "EXISTS" : "NOT_FOUND";
            sendText(exchange, 200, response);
        } catch (Exception e) {
            sendText(exchange, 500, "Check failed: " + e.getMessage());
        }
    }
}
