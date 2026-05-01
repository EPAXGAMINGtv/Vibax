package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;

public class MakeConfigHandler extends AuthenticatedHandler implements HttpHandler {

    public MakeConfigHandler(String passwordHash) {
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
        if (query == null || !query.startsWith("name=")) {
            sendText(exchange, 400, "Missing ?name=");
            return;
        }

        String name = query.substring("name=".length());

        try {
            FileManager.getConfigFile(name);
            sendText(exchange, 200, "Config created: " + name);
        } catch (Exception e) {
            sendText(exchange, 500, "Config not created");
        }
    }
}
