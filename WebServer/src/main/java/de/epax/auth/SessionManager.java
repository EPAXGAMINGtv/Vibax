package de.epax.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Map<String, String> sessions = new ConcurrentHashMap<>();
    private static final Map<String, Long> expiry = new ConcurrentHashMap<>();
    private static final long SESSION_TTL_MS = 24 * 60 * 60 * 1000L;

    public static String createSession(String username) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, username);
        expiry.put(token, System.currentTimeMillis() + SESSION_TTL_MS);
        return token;
    }

    public static String getUsername(String token) {
        if (token == null) return null;
        Long exp = expiry.get(token);
        if (exp == null || System.currentTimeMillis() > exp) {
            sessions.remove(token);
            expiry.remove(token);
            return null;
        }
        return sessions.get(token);
    }

    public static void invalidate(String token) {
        sessions.remove(token);
        expiry.remove(token);
    }
}
