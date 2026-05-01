package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class WriteConfigHandler extends AuthenticatedHandler implements HttpHandler {

    public WriteConfigHandler(String passwordHash) {
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

        try (InputStream in = exchange.getRequestBody()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            FileManager.writeConfig(name, content);
            sendText(exchange, 200, "Config saved: " + name);
        } catch (Exception e) {
            sendText(exchange, 500, "Failed to save config");
        }
    }
}
