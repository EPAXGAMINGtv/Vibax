package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class UploadHandler extends AuthenticatedHandler implements HttpHandler {

    public UploadHandler(String passwordHash) {
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
        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            FileManager.createFile(path);
        } catch (Exception e) {
            sendText(exchange, 500, "Cannot create file");
            return;
        }

        try (InputStream in = exchange.getRequestBody();
             FileOutputStream out = new FileOutputStream(FileManager.resolveSafePath(path))) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }

            sendText(exchange, 200, "Uploaded to " + path);

        } catch (Exception e) {
            sendText(exchange, 500, "Upload failed");
        }
    }
}
