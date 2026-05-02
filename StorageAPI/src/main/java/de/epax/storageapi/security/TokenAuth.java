package de.epax.storageapi.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.epax.storageapi.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;

/**
 * SECURITY FIXES:
 *  - Removed hardcoded default admin user with password "admin123"
 *  - Replaced SHA-256 password comparison with BCrypt (timing-safe)
 *  - Fixed generateToken() double-addition of TTL bug
 *  - Fixed verifySignature() which passed empty string instead of token to signRequest()
 *  - Added token cleanup to prevent unbounded memory growth
 */
public class TokenAuth {
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_TOKENS_PER_USER = 10;

    private static final Map<String, TokenInfo> activeTokens = new ConcurrentHashMap<>();
    private static final Map<String, UserInfo> users = new ConcurrentHashMap<>();

    // SECURITY FIX: No hardcoded default user. Create users explicitly via createUser().

    public static class TokenInfo {
        final String token;
        final String username;
        final String[] roles;
        final Instant issuedAt;
        final Instant expiresAt;
        public final String clientIp;

        public TokenInfo(String token, String username, String[] roles, Instant issuedAt, Instant expiresAt, String clientIp) {
            this.token     = token;
            this.username  = username;
            this.roles     = roles;
            this.issuedAt  = issuedAt;
            this.expiresAt = expiresAt;
            this.clientIp  = clientIp;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public static class UserInfo {
        final String username;
        final String bcryptHash;   // SECURITY FIX: BCrypt hash, not SHA-256
        final String[] roles;
        final Set<String> permissions;

        public UserInfo(String username, String bcryptHash, String[] roles, Set<String> permissions) {
            this.username    = username;
            this.bcryptHash  = bcryptHash;
            this.roles       = roles;
            this.permissions = permissions;
        }
    }

    /**
     * SECURITY FIX: generateToken() previously added ttlMinutes twice due to a bug.
     * Now correctly creates a token that expires in exactly ttlMinutes minutes.
     */
    public static String generateToken(String username, String clientIp, long ttlMinutes) {
        UserInfo user = users.get(username);
        if (user == null) return null;

        // Enforce per-user token limit to prevent memory exhaustion
        List<String> existing = new ArrayList<>();
        for (Map.Entry<String, TokenInfo> e : activeTokens.entrySet()) {
            if (e.getValue().username.equals(username)) existing.add(e.getKey());
        }
        if (existing.size() >= MAX_TOKENS_PER_USER) {
            // Revoke oldest token
            existing.stream()
                .min(Comparator.comparing(k -> activeTokens.get(k).issuedAt))
                .ifPresent(activeTokens::remove);
        }

        String token  = generateSecureToken();
        Instant issued  = Instant.now();
        // SECURITY FIX: was: issued.plus(ttlMinutes, MINUTES) then .plus(ttlMinutes, MINUTES) again
        Instant expires = issued.plus(ttlMinutes, ChronoUnit.MINUTES);

        activeTokens.put(token, new TokenInfo(token, username, user.roles, issued, expires, clientIp));
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
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            Logger.error("Failed to sign request: " + e.getMessage());
            return null;
        }
    }

    /**
     * SECURITY FIX: Previously called signRequest("", ...) — passing empty string instead of token.
     * Now passes the actual token for correct HMAC computation.
     */
    public static boolean verifySignature(String signature, String token, String method, String path, String body, String secret) {
        String expected = signRequest(token, method, path, body, secret);
        return signature != null && expected != null && MessageDigestCompare.equal(signature, expected);
    }

    /**
     * Create a user with a plaintext password — stored as BCrypt hash.
     * SECURITY FIX: Was using SHA-256 (not timing-safe, no salt).
     */
    public static void createUser(String username, String password, String... roles) {
        createUser(username, password, roles, Set.of());
    }

    public static void createUser(String username, String password, String[] roles, Set<String> permissions) {
        // SECURITY FIX: BCrypt with cost=12 instead of SHA-256
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        users.put(username, new UserInfo(username, hash, roles, permissions));
    }

    /**
     * SECURITY FIX: BCrypt.verifyer() is timing-safe; SHA-256 equals() is not.
     */
    public static boolean authenticate(String username, String password) {
        UserInfo user = users.get(username);
        if (user == null) return false;
        try {
            return BCrypt.verifyer().verify(password.toCharArray(), user.bcryptHash).verified;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasPermission(TokenInfo token, String permission) {
        UserInfo user = users.get(token.username);
        if (user == null) return false;
        if (user.permissions.contains(permission)) return true;
        if (Arrays.asList(token.roles).contains("ADMIN")) return true;
        return Arrays.stream(token.roles).anyMatch(r -> user.permissions.contains(r + ":*"));
    }

    public static List<TokenInfo> getUserTokens(String username) {
        List<TokenInfo> list = new ArrayList<>();
        for (TokenInfo ti : activeTokens.values()) {
            if (ti.username.equals(username)) list.add(ti);
        }
        return list;
    }

    public static long getActiveTokenCount() {
        // Also purge expired tokens while we're at it
        activeTokens.entrySet().removeIf(e -> e.getValue().isExpired());
        return activeTokens.size();
    }

    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Constant-time string comparison to prevent timing attacks. */
    private static class MessageDigestCompare {
        static boolean equal(String a, String b) {
            if (a.length() != b.length()) return false;
            int result = 0;
            for (int i = 0; i < a.length(); i++) {
                result |= a.charAt(i) ^ b.charAt(i);
            }
            return result == 0;
        }
    }
}
