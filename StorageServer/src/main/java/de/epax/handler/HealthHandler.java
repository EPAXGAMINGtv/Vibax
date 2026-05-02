package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HealthHandler extends JsonHandler implements HttpHandler {

    public HealthHandler(String passwordHash) {
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

        Map<String, Object> health = new ConcurrentHashMap<>();
        health.put("status", "healthy");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("uptime", getUptime());
        health.put("memory", getMemoryStats());
        health.put("storagePath", getStoragePath());
        
        // Add disk space information
        try {
            java.io.File root = new java.io.File(getStoragePath());
            health.put("totalSpace", root.getTotalSpace());
            health.put("freeSpace", root.getFreeSpace());
        } catch (Exception e) {
            health.put("totalSpace", -1L);
            health.put("freeSpace", -1L);
        }

        sendJson(exchange, 200, health);
    }

    private String getUptime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptime = rb.getUptime();
        long days = TimeUnit.MILLISECONDS.toDays(uptime);
        long hours = TimeUnit.MILLISECONDS.toHours(uptime) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(uptime) % 60;
        return String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);
    }

    private Map<String, Object> getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> mem = new ConcurrentHashMap<>();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        mem.put("totalMB", total / (1024 * 1024));
        mem.put("usedMB", used / (1024 * 1024));
        mem.put("freeMB", free / (1024 * 1024));
        mem.put("maxMB", runtime.maxMemory() / (1024 * 1024));
        return mem;
    }

    private String getStoragePath() {
        try {
            return de.epax.StorageServerStart.getStoragePath();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
