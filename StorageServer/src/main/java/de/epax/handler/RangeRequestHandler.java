package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

public class RangeRequestHandler extends AuthenticatedHandler implements HttpHandler {

    public RangeRequestHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();

        if (query == null || !query.startsWith("path=")) {
            sendText(exchange, 400, "Missing ?path=");
            return;
        }

        String path = query.substring("path=".length());

        try {
            File file = FileManager.resolveSafePath(path);
            if (!file.exists() || !file.isFile()) {
                sendText(exchange, 404, "File not found");
                return;
            }

            long fileSize = file.length();
            String range = exchange.getRequestHeaders().getFirst("Range");

            if (range == null || "GET".equalsIgnoreCase(method)) {
                // Full download
                serveFullFile(exchange, file);
            } else if (range != null && range.startsWith("bytes=")) {
                // Partial content request
                servePartialFile(exchange, file, fileSize, range);
            } else {
                sendText(exchange, 400, "Invalid range header");
            }
        } catch (Exception e) {
            sendText(exchange, 500, "Error: " + e.getMessage());
        }
    }

    private void serveFullFile(HttpExchange exchange, File file) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(file.length()));
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.sendResponseHeaders(200, file.length());

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel();
             OutputStream out = exchange.getResponseBody()) {
            WritableByteChannel outChannel = Channels.newChannel(out);
            channel.transferTo(0, file.length(), outChannel);
        }
    }

    private void servePartialFile(HttpExchange exchange, File file, long fileSize, String range) throws IOException {
        String[] parts = range.substring(6).split("-");
        long start = Long.parseLong(parts[0].trim());
        long end = parts.length > 1 && !parts[1].isEmpty()
                ? Long.parseLong(parts[1].trim())
                : fileSize - 1;

        if (start >= fileSize || end >= fileSize) {
            exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileSize);
            sendText(exchange, 416, "Requested range not satisfiable");
            return;
        }

        long contentLength = end - start + 1;
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
        exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.sendResponseHeaders(206, contentLength);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel();
             OutputStream out = exchange.getResponseBody()) {
            WritableByteChannel outChannel = Channels.newChannel(out);
            channel.transferTo(start, contentLength, outChannel);
        }
    }
}
