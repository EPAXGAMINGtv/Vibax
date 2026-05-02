package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * SECURITY FIX:
 *  - Added max upload size limit (default 100 MB, configurable via system property vibax.upload.maxBytes)
 *  - Added URL-decoding for the path parameter
 *  - Returns 413 Payload Too Large if exceeded
 */
public class UploadHandler extends AuthenticatedHandler implements HttpHandler {

    private static final long MAX_UPLOAD_BYTES = Long.parseLong(
            System.getProperty("vibax.upload.maxBytes", String.valueOf(100L * 1024 * 1024))
    );

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

        // SECURITY FIX: URL-decode the path parameter
        String path = URLDecoder.decode(query.substring("path=".length()), StandardCharsets.UTF_8);

        try {
            FileManager.createFile(path);
        } catch (Exception e) {
            sendText(exchange, 500, "Cannot create file");
            return;
        }

        // SECURITY FIX: Enforce upload size limit
        try (InputStream in = exchange.getRequestBody();
             FileOutputStream out = new FileOutputStream(FileManager.resolveSafePath(path))) {

            byte[] buffer = new byte[8192];
            long written = 0;
            int len;
            while ((len = in.read(buffer)) != -1) {
                written += len;
                if (written > MAX_UPLOAD_BYTES) {
                    sendText(exchange, 413, "Payload Too Large (max " + MAX_UPLOAD_BYTES + " bytes)");
                    return;
                }
                out.write(buffer, 0, len);
            }

            sendText(exchange, 200, "Uploaded to " + path);

        } catch (Exception e) {
            sendText(exchange, 500, "Upload failed");
        }
    }
}
