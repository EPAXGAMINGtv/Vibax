package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class AuthenticatedHandler {

    protected final String passwordHash;

    public AuthenticatedHandler(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    protected boolean isAuthorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-Password-Hash");
        return passwordHash.equals(header);
    }

    protected void sendUnauthorized(HttpExchange exchange) throws IOException {
        sendText(exchange, 401, "Unauthorized: missing or wrong X-Password-Hash header");
    }

    protected void sendText(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}