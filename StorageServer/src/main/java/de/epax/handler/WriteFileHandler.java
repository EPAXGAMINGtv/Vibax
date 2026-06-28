package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class WriteFileHandler extends AuthenticatedHandler implements HttpHandler {

    private static final long MAX_WRITE_BYTES = Long.parseLong(
            System.getProperty("vibax.upload.maxBytes", String.valueOf(100L * 1024 * 1024))
    );

    public WriteFileHandler(String passwordHash) {
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

        String path = URLDecoder.decode(query.substring("path=".length()), StandardCharsets.UTF_8);

        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            long written = 0;
            int len;
            while ((len = in.read(buffer)) != -1) {
                written += len;
                if (written > MAX_WRITE_BYTES) {
                    sendText(exchange, 413, "Payload Too Large");
                    return;
                }
                baos.write(buffer, 0, len);
            }

            FileManager.writeFile(path, baos.toString(StandardCharsets.UTF_8.name()));
            sendText(exchange, 200, "Written to " + path);

        } catch (Exception e) {
            sendText(exchange, 500, "Write failed: " + e.getMessage());
        }
    }
}
