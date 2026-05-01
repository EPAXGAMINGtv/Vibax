package de.epax.storageapi.internet;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpConnection {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .build();


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
                                    URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
                    )
                    .collect(Collectors.joining("&"));

            url += (url.contains("?") ? "&" : "?") + queryString;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        if (passwordHash != null) {
            builder.header("X-Password-Hash", passwordHash);
        }

        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }

        switch (method.toUpperCase()) {
            case "POST":
                builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                break;
            case "PUT":
                builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                builder.GET();
        }

        HttpRequest request = builder.build();

        HttpResponse<String> response = CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        return response.body();
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

    public static String getServerName(String url,String pwhash){
        try {
            String response = sendRequest(url, "GET", pwhash, null, null, null);
            return response.trim();
        } catch (Exception e) {
            return "didn't return any name";
        }
    }
}