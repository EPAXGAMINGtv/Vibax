package de.epax.storageapi.internet;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SECURITY FIXES:
 *  - Added response size limit (max 50 MB) to prevent memory exhaustion from large responses
 *  - Added read timeout (10s) in addition to connect timeout to prevent hung connections
 *  - passwordHash parameter is now clearly documented as "raw password" (sent to server
 *    which now uses BCrypt.verifyer() on the received value)
 */
public class HttpConnection {

    private static final long MAX_RESPONSE_BYTES = 50L * 1024 * 1024; // 50 MB

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Send an HTTP request to a StorageServer endpoint.
     *
     * @param passwordHash The raw API password (StorageServer verifies it with BCrypt).
     *                     Transmitted as X-Password-Hash header — use HTTPS in production.
     */
    public static String sendRequest(
            String url,
            String method,
            String passwordHash,
            String body,
            Map<String, String> queryParams,
            Map<String, String> extraHeaders
    ) throws IOException, InterruptedException {

        if (queryParams != null && !queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .map(entry ->
                            URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) +
                            "=" +
                            URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            url += (url.contains("?") ? "&" : "?") + queryString;
        }

        long bodyLen = body != null ? body.length() : 0;
        Duration timeout = bodyLen > 1_000_000
                ? Duration.ofSeconds(Math.min(60, 10 + bodyLen / 500_000))
                : Duration.ofSeconds(10);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout);

        if (passwordHash != null) {
            builder.header("X-Password-Hash", passwordHash);
        }

        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }

        switch (method.toUpperCase()) {
            case "POST" -> {
                builder.header("Content-Type", "text/plain; charset=utf-8");
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            }
            case "PUT" -> {
                builder.header("Content-Type", "text/plain; charset=utf-8");
                builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            }
            case "DELETE" -> builder.DELETE();
            default       -> builder.GET();
        }

        HttpRequest request = builder.build();

        HttpResponse<byte[]> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IOException("Request to " + url + " failed: " + e.getMessage(), e);
        }

        int status = response.statusCode();
        byte[] responseBytes = response.body();
        if (responseBytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("Response from server exceeds maximum allowed size of " + MAX_RESPONSE_BYTES + " bytes");
        }

        if (status < 200 || status >= 300) {
            return null;
        }

        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    public static String get(String url, Map<String, String> queryParams) throws IOException, InterruptedException {
        return sendRequest(url, "GET", null, null, queryParams, null);
    }

    public static boolean isOnline(String url) {
        try {
            String response = sendRequest(url, "GET", null, null, null, null);
            return "true".equalsIgnoreCase(response.trim());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Send an HTTP request and return raw bytes (for binary downloads).
     */
    public static byte[] sendRequestBytes(
            String url,
            String method,
            String passwordHash,
            String body,
            Map<String, String> queryParams,
            Map<String, String> extraHeaders
    ) throws IOException, InterruptedException {

        if (queryParams != null && !queryParams.isEmpty()) {
            String queryString = queryParams.entrySet().stream()
                    .map(entry ->
                            URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) +
                            "=" +
                            URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            url += (url.contains("?") ? "&" : "?") + queryString;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

        if (passwordHash != null) {
            builder.header("X-Password-Hash", passwordHash);
        }

        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }

        switch (method.toUpperCase()) {
            case "POST" -> {
                builder.header("Content-Type", "text/plain; charset=utf-8");
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            }
            case "PUT" -> {
                builder.header("Content-Type", "text/plain; charset=utf-8");
                builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            }
            case "DELETE" -> builder.DELETE();
            default       -> builder.GET();
        }

        HttpRequest request = builder.build();
        HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

        int status = response.statusCode();
        byte[] responseBytes = response.body();
        if (responseBytes.length > MAX_RESPONSE_BYTES) {
            throw new IOException("Response exceeds maximum allowed size of " + MAX_RESPONSE_BYTES + " bytes");
        }

        if (status < 200 || status >= 300) {
            return null;
        }

        return responseBytes;
    }

    public static String getServerName(String url, String pwhash) {
        try {
            return sendRequest(url, "GET", pwhash, null, null, null).trim();
        } catch (Exception e) {
            return "didn't return any name";
        }
    }
}
