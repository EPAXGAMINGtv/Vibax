package de.epax.web;

import com.sun.net.httpserver.HttpServer;
import de.epax.storageapi.logging.Logger;
import de.epax.storageapi.manager.PropertiesManager;


public class WebServerHTTP {
    private HttpServer server;
    private final int port;
    private final int maxThreads;
    public WebServerHTTP(int port, int maxThreads) {
        this.port = port;
        this.maxThreads = maxThreads;
        createServer();
    }

    private void createServer(){
        Logger.info("Creating HTTPServer...");

    }

}
