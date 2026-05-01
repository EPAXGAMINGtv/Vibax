package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;

public class RecursiveDeleteHandler extends AuthenticatedHandler implements HttpHandler {

    public RecursiveDeleteHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("DELETE")) {
            sendText(exchange, 405, "Only DELETE allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();
        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            boolean deleted = FileManager.delete(path);
            if (deleted) {
                sendText(exchange, 200, "Deleted: " + path);
            } else {
                sendText(exchange, 404, "Delete failed - path not found");
            }
        } catch (Exception e) {
            sendText(exchange, 500, "Delete failed: " + e.getMessage());
        }
    }
}
