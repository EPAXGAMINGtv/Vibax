package de.epax.storageapi.security;

import de.epax.storageapi.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Base64;

public class TokenAuth {
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, TokenInfo> activeTokens = new ConcurrentHashMap<>();
    private static final Map<String, UserInfo> users = new ConcurrentHashMap<>();

    static {
        // Add default admin user
        createUser("admin", "admin123", new String[]{"ADMIN"}, Set.of("*"));
    }

    public static class TokenInfo {
        final String token;
        final String username;
        final String[] roles;
        final Instant issuedAt;
        final Instant expiresAt;
        public final String clientIp;

        public TokenInfo(String token, String username, String[] roles, Instant issuedAt, Instant expiresAt, String clientIp) {
            this.token = token;
            this.username = username;
            this.roles = roles;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.clientIp = clientIp;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public static class UserInfo {
        final String username;
        final String passwordHash;
        final String[] roles;
        final Set<String> permissions;

        public UserInfo(String username, String passwordHash, String[] roles, Set<String> permissions) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.roles = roles;
            this.permissions = permissions;
        }
    }

    public static String generateToken(String username, String clientIp, long ttlMinutes) {
        UserInfo user = users.get(username);
        if (user == null) return null;

        String token = generateSecureToken();
        Instant issued = Instant.now();
        Instant expires = issued.plus(ttlMinutes, TimeUnit.SECONDS.toChronoUnit() == null ? java.time.temporal.ChronoUnit.MINUTES : java.time.temporal.ChronoUnit.MINUTES);

        TokenInfo info = new TokenInfo(token, username, user.roles, issued, expires.plus(ttlMinutes, java.time.temporal.ChronoUnit.MINUTES), clientIp);
        activeTokens.put(token, info);

        return token;
    }

    public static TokenInfo validateToken(String token) {
        TokenInfo info = activeTokens.get(token);
        if (info == null) return null;
        if (info.isExpired()) {
            activeTokens.remove(token);
            return null;
        }
        return info;
    }

    public static boolean revokeToken(String token) {
        return activeTokens.remove(token) != null;
    }

    public static void revokeAllUserTokens(String username) {
        activeTokens.entrySet().removeIf(e -> e.getValue().username.equals(username));
    }

    public static String signRequest(String token, String method, String path, String body, String secret) {
        try {
            String data = method + "\n" + path + "\n" + (body != null ? body : "");
            Mac mac = Mac.getInstance(HMAC_ALGO);
            Key key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(key);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            Logger.error("Failed to sign request: " + e.getMessage());
            return null;
        }
    }

    public static boolean verifySignature(String signature, String method, String path, String body, String secret) {
        String expected = signRequest("", method, path, body, secret);
        return signature != null && signature.equals(expected);
    }

    public static void createUser(String username, String password, String... roles) {
        createUser(username, password, roles, Set.of());
    }

    public static void createUser(String username, String password, String[] roles, Set<String> permissions) {
        String hash = sha256(password);
        users.put(username, new UserInfo(username, hash, roles, permissions));
    }

    public static boolean authenticate(String username, String password) {
        UserInfo user = users.get(username);
        if (user == null) return false;
        return user.passwordHash.equals(sha256(password));
    }

    public static boolean hasPermission(TokenInfo token, String permission) {
        UserInfo user = users.get(token.username);
        if (user == null) return false;
        // Check direct permission
        if (user.permissions.contains(permission)) return true;
        // Check wildcard admin
        if (Arrays.asList(token.roles).contains("ADMIN")) return true;
        // Check role-based
        return Arrays.asList(token.roles).stream().anyMatch(r -> user.permissions.contains(r + ":*"));
    }

    public static List<TokenInfo> getUserTokens(String username) {
        List<TokenInfo> list = new ArrayList<>();
        for (TokenInfo ti : activeTokens.values()) {
            if (ti.username.equals(username)) list.add(ti);
        }
        return list;
    }

    public static long getActiveTokenCount() {
        return activeTokens.size();
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
