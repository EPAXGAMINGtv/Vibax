package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ListFilesHandler extends AuthenticatedHandler implements HttpHandler {

    public ListFilesHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendText(exchange, 405, "Only GET allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();

        String path = "/";
        if (query != null && query.startsWith("path=")) {
            path = query.substring("path=".length());
        }

        List<String> items = FileManager.list(path);

        StringBuilder sb = new StringBuilder();
        sb.append("Listing for: ").append(path).append("\n\n");

        if (items.isEmpty()) {
            sb.append("(empty)");
        } else {
            for (String item : items) {
                boolean isDirectory = false;
                try {
                    FileManager.list(path.equals("/") ? "/" + item : path + "/" + item);

                    isDirectory = FileManager.isDirectory(path.equals("/") ? "/" + item : path + "/" + item);
                } catch (Exception e) {
                    isDirectory = false;
                }
                if (isDirectory) {
                    sb.append("[DIR] ").append(item).append("\n");
                } else {
                    sb.append(item).append("\n");
                }
            }
        }

        sendText(exchange, 200, sb.toString());
    }
}
