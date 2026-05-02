package de.epax.storageapi.security;

import de.epax.storageapi.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * SECURITY FIX:
 *  - verify() now uses MessageDigest.isEqual() (constant-time comparison)
 *    instead of signature.equals(expected) which is vulnerable to timing attacks.
 */
public class RequestSigner {
    private static final String HMAC_ALGO = "HmacSHA256";

    public static String sign(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload));
    }

    /**
     * SECURITY FIX: Use constant-time comparison to prevent timing attacks.
     * signature.equals(expected) leaks timing information about how many bytes match.
     */
    public static boolean verify(String signature, byte[] payload, String secret) throws Exception {
        String expected = sign(payload, secret);
        return signature != null && MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String signString(String data, String secret) throws Exception {
        return sign(data.getBytes(StandardCharsets.UTF_8), secret);
    }

    public static boolean verifyString(String signature, String data, String secret) throws Exception {
        return verify(signature, data.getBytes(StandardCharsets.UTF_8), secret);
    }

    public static Map<String, String> generateSignedHeaders(String method, String path, String body, String secret) {
        Map<String, String> headers = new HashMap<>();
        try {
            String timestamp = String.valueOf(Instant.now().toEpochMilli());
            String data      = method + "\n" + path + "\n" + timestamp + "\n" + (body != null ? body : "");
            headers.put("X-Timestamp", timestamp);
            headers.put("X-Signature", signString(data, secret));
        } catch (Exception e) {
            Logger.error("Failed to generate signed headers: " + e.getMessage());
        }
        return headers;
    }

    public static boolean verifySignedRequest(String method, String path, String body,
                                               String timestampStr, String signature, String secret) {
        try {
            long timestamp = Long.parseLong(timestampStr);
            long age = Instant.now().toEpochMilli() - timestamp;
            if (age > 300_000L || age < -30_000L) {  // also reject future timestamps > 30s
                Logger.warn("Request timestamp outside allowed window: " + timestamp);
                return false;
            }
            String data = method + "\n" + path + "\n" + timestampStr + "\n" + (body != null ? body : "");
            return verify(signature, data.getBytes(StandardCharsets.UTF_8), secret);
        } catch (Exception e) {
            return false;
        }
    }

    public static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(
                    digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { return null; }
    }

    public static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return null; }
    }
}
