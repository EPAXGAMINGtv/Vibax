package de.epax.handler;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sun.net.httpserver.HttpExchange;
import de.epax.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECURITY FIX:
 *  - Replaced plain SHA-256 comparison with BCrypt.verifyer() (timing-safe)
 *  - Added IP-based rate limiting: max 10 failed attempts per 60s per IP
 *  - passwordHash field now expects a BCrypt hash in storageserver.properties
 */
public abstract class AuthenticatedHandler {

    private static final int  MAX_FAILURES = 10;
    private static final long WINDOW_MS    = 60_000L;

    // IP -> [failureCount, windowStartMs]
    private static final Map<String, long[]> rateLimitMap = new ConcurrentHashMap<>();

    protected final String passwordHash;

    public AuthenticatedHandler(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    protected boolean isAuthorized(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

        if (isRateLimited(ip)) {
            Logger.warn("Rate limit exceeded for IP: " + ip);
            return false;
        }

        String header = exchange.getRequestHeaders().getFirst("X-Password-Hash");
        if (header == null || header.isBlank()) {
            recordFailure(ip);
            return false;
        }

        // BCrypt timing-safe verify (header = raw password sent by client)
        boolean valid;
        try {
            valid = BCrypt.verifyer()
                    .verify(header.toCharArray(), passwordHash)
                    .verified;
        } catch (Exception e) {
            Logger.error("BCrypt verify error: " + e.getMessage());
            valid = false;
        }

        if (!valid) {
            recordFailure(ip);
            Logger.warn("Failed auth attempt from IP: " + ip);
        } else {
            clearFailures(ip);
        }
        return valid;
    }

    private boolean isRateLimited(String ip) {
        long[] state = rateLimitMap.get(ip);
        if (state == null) return false;
        long now = System.currentTimeMillis();
        if (now - state[1] > WINDOW_MS) {
            rateLimitMap.remove(ip);
            return false;
        }
        return state[0] >= MAX_FAILURES;
    }

    private void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        rateLimitMap.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) return new long[]{1, now};
            v[0]++;
            return v;
        });
    }

    private void clearFailures(String ip) {
        rateLimitMap.remove(ip);
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
