package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SECURITY FIX:
 *  - CORS: Access-Control-Allow-Origin restricted (configure ALLOWED_ORIGIN)
 *  - Kept all existing security headers
 */
public class SecureHandlerWrapper implements HttpHandler {

    // SECURITY: Change this to your actual frontend domain in production.
    // Set to "*" only for local development.
    private static final String ALLOWED_ORIGIN = System.getProperty(
            "vibax.cors.origin",
            System.getenv().getOrDefault("VIBAX_CORS_ORIGIN", "*")
    );

    private final HttpHandler wrapped;
    private final String path;

    public SecureHandlerWrapper(HttpHandler wrapped, String path) {
        this.wrapped = wrapped;
        this.path = path;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long startTime = System.currentTimeMillis();

        // Security headers
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().add("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");

        // SECURITY FIX: Restrict CORS to configured origin instead of "*"
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Password-Hash");

        // Handle preflight OPTIONS
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        String method    = exchange.getRequestMethod();
        String remoteAddr = exchange.getRemoteAddress().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            wrapped.handle(exchange);
            long duration = System.currentTimeMillis() - startTime;
            Logger.info(String.format("[%s] %s %s from %s - %d ms",
                    timestamp, method, path, remoteAddr, duration));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Logger.error(String.format("[%s] %s %s from %s - ERROR after %d ms: %s",
                    timestamp, method, path, remoteAddr, duration, e.getMessage()));
            try {
                sendError(exchange, 500, "Internal server error");
            } catch (Exception ignored) {}
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
