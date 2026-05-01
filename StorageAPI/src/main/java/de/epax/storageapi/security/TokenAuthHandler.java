package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.storageapi.security.TokenAuth;
import de.epax.storageapi.logging.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class TokenAuthHandler implements HttpHandler {

    protected final String authHeaderName = "Authorization";
    protected final String bearerPrefix = "Bearer ";

    protected boolean isAuthorized(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst(authHeaderName);
        if (auth == null || !auth.startsWith(bearerPrefix)) {
            return false;
        }

        String token = auth.substring(bearerPrefix.length());
        TokenAuth.TokenInfo info = TokenAuth.validateToken(token);
        if (info == null) {
            return false;
        }

        // Check IP match if client IP provided
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (info.clientIp != null && !info.clientIp.equals(clientIp)) {
            Logger.warn("Token IP mismatch: token from " + info.clientIp + " but request from " + clientIp);
            return false;
        }

        return true;
    }

    protected TokenAuth.TokenInfo getTokenInfo(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst(authHeaderName);
        if (auth == null || !auth.startsWith(bearerPrefix)) {
            return null;
        }
        return TokenAuth.validateToken(auth.substring(bearerPrefix.length()));
    }

    protected void sendUnauthorized(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 401, Map.of("error", "unauthorized", "message", message));
    }

    protected void sendForbidden(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 403, Map.of("error", "forbidden", "message", message));
    }

    protected void sendSuccess(HttpExchange exchange, Object data) throws IOException {
        sendJson(exchange, 200, data instanceof Map ? (Map<?, ?>) data : Map.of("success", true, "data", data));
    }

    protected void sendJson(HttpExchange exchange, int code, Map<?, ?> map) throws IOException {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(e.getKey()).append("\":\"")
                .append(String.valueOf(e.getValue()).replace("\"", "\\\"")).append("\"");
            first = false;
        }
        json.append("}");
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
