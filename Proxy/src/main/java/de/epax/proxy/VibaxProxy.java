package de.epax.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reverse proxy / load balancer for multiple Vibax WebServer instances.
 * Routes requests round-robin to configured backend servers.
 */
public class VibaxProxy {

    private static final AtomicInteger roundRobin = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        List<String> backends = new ArrayList<>();
        if (args.length > 1) {
            for (int i = 1; i < args.length; i++) {
                backends.add(args[i]);
            }
        } else {
            backends.add("http://127.0.0.1:8081");
            backends.add("http://127.0.0.1:8082");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(50));

        server.createContext("/", new ProxyHandler(client, backends));
        server.start();

        System.out.println("Vibax Proxy running on http://localhost:" + port);
        System.out.println("Backends: " + String.join(", ", backends));
    }

    static class ProxyHandler implements HttpHandler {
        private final HttpClient client;
        private final List<String> backends;

        ProxyHandler(HttpClient client, List<String> backends) {
            this.client = client;
            this.backends = backends;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (backends.isEmpty()) {
                sendError(exchange, 503, "No backends configured");
                return;
            }

            int idx = Math.floorMod(roundRobin.getAndIncrement(), backends.size());
            String backend = backends.get(idx);
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String targetUrl = backend + path + (query != null ? "?" + query : "");

            try {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(Duration.ofSeconds(30));

                String method = exchange.getRequestMethod();
                byte[] body = readAll(exchange.getRequestBody());

                switch (method) {
                    case "GET" -> reqBuilder.GET();
                    case "DELETE" -> reqBuilder.DELETE();
                    case "HEAD" -> reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                    default -> reqBuilder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
                }

                exchange.getRequestHeaders().forEach((key, values) -> {
                    if (!isHopByHopHeader(key)) {
                        values.forEach(v -> reqBuilder.header(key, v));
                    }
                });
                reqBuilder.header("X-Forwarded-For", exchange.getRemoteAddress().getAddress().getHostAddress());
                reqBuilder.header("X-Forwarded-Proto", "http");

                HttpResponse<byte[]> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

                exchange.getResponseHeaders().clear();
                response.headers().map().forEach((key, values) -> {
                    if (!isHopByHopHeader(key)) {
                        values.forEach(v -> exchange.getResponseHeaders().add(key, v));
                    }
                });
                exchange.getResponseHeaders().add("X-Vibax-Backend", backend);

                exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.body());
                }
            } catch (Exception e) {
                sendError(exchange, 502, "Backend unavailable: " + e.getMessage());
            }
        }

        private static boolean isHopByHopHeader(String name) {
            String lower = name.toLowerCase();
            return lower.equals("connection") || lower.equals("keep-alive")
                    || lower.equals("transfer-encoding") || lower.equals("host");
        }

        private static byte[] readAll(InputStream in) throws IOException {
            return in.readAllBytes();
        }

        private static void sendError(HttpExchange exchange, int code, String msg) throws IOException {
            byte[] bytes = msg.getBytes();
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
