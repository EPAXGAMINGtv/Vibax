package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;

public class ReadFileHandler extends AuthenticatedHandler implements HttpHandler {

    public ReadFileHandler(String passwordHash) {
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

        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            String content = FileManager.readFile(path);

            sendText(exchange, 200, content);

        } catch (Exception e) {
            sendText(exchange, 404, "File not found");
        }
    }
}