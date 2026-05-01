package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.StorageServerStart;
import de.epax.file.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SearchFileHandler extends AuthenticatedHandler implements HttpHandler {

    public SearchFileHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String query = exchange.getRequestURI().getQuery();

        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing path");
            return;
        }

        String path = query.substring("path=".length());

        File root = new File(StorageServerStart.getStoragePath());

        boolean found = searchRecursive(root, path);

        if (found) {
            sendText(exchange, 200, "FOUND:" + path);
        } else {
            sendText(exchange, 200, "NOT_FOUND");
        }

    }

    private boolean searchRecursive(File dir, String targetName) {

        if (dir == null || !dir.exists()) return false;

        File[] files = dir.listFiles();
        if (files == null) return false;

        for (File f : files) {

            if (f.isDirectory()) {
                if (searchRecursive(f, targetName)) return true;
            } else {
                if (f.getName().equalsIgnoreCase(targetName)) {
                    return true;
                }
            }
        }

        return false;
    }
}