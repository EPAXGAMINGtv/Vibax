package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class DownloadHandler extends AuthenticatedHandler implements HttpHandler {

    public DownloadHandler(String passwordHash) {
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

        String path = URLDecoder.decode(query.substring("path=".length()), StandardCharsets.UTF_8);

        File file;
        try {
            file = FileManager.resolveSafePath(path);
        } catch (Exception e) {
            sendText(exchange, 404, "File not found");
            return;
        }

        if (!file.exists() || !file.isFile()) {
            sendText(exchange, 404, "File not found");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        exchange.sendResponseHeaders(200, file.length());

        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = exchange.getResponseBody()) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
}
