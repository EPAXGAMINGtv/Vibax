package de.epax.storageapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class OnlineHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        StorageAPI.checkServers();

        String html = "";

        html += "<html>";
        html += "<head>";
        html += "<title>Server Status</title>";
        html += "<style>";
        html += "body{font-family:Arial;background:#0f172a;color:white;padding:20px;}";
        html += ".server{background:#1e293b;padding:10px;margin:10px;border-radius:8px;}";
        html += ".online{color:green;font-weight:bold;}";
        html += ".offline{color:red;font-weight:bold;}";
        html += "</style>";
        html += "</head>";
        html += "<body>";

        html += "<h1>Server Status</h1>";

        for (var entry : StorageAPI.getServers().entrySet()) {

            int id = entry.getKey();
            ServerConfig server = entry.getValue();

            html += "<div class='server'>";
            html += "Server:" + server.name  + " (" + server.ip + ") ";
            html += "<span class='" + (server.online ? "online" : "offline") + "'>";
            html += (server.online ? "ONLINE" : "OFFLINE");
            html += "</span>";
            html += "</div>";
        }

        html += "</body>";
        html += "</html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}