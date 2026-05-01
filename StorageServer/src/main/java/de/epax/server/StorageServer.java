package de.epax.server;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.epax.logging.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class StorageServer {

    private HttpServer server;
    private final int port;
    private final int maxThreads;

    public StorageServer(int port, int maxThreads) {
        this.port = port;
        this.maxThreads = maxThreads;
        createServer();
    }

    private void createServer() {
        Logger.info("Creating HTTPServer...");

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(maxThreads));

            Logger.info("HTTPServer created on port " + port + " with " + maxThreads + " threads");

        } catch (IOException e) {
            Logger.error("Failed to create HTTPServer: " + e.getMessage());
            throw new RuntimeException("Server creation failed", e);
        }
    }

    public void startServer() {
        if (server == null) {
            Logger.error("Server is NULL - cannot start");
            return;
        }

        Logger.info("Starting HTTPServer...");
        server.start();
        Logger.info("HTTPServer started successfully");
    }

    public void addServerHandler(HttpHandler handler, String reachPath) {
        if (server == null) {
            Logger.error("Server is NULL - cannot register handler: " + reachPath);
            return;
        }

        Logger.info("Registering handler " + handler.getClass().getSimpleName() + " on " + reachPath);
        server.createContext(reachPath, handler);
        Logger.info("registert handler"+handler.getClass().toString()+" on "+reachPath);
    }
}