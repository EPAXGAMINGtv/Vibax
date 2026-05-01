package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsHandler extends JsonHandler implements HttpHandler {

    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong failedRequests = new AtomicLong(0);
    private static final AtomicLong bytesUploaded = new AtomicLong(0);
    private static final AtomicLong bytesDownloaded = new AtomicLong(0);

    public MetricsHandler(String passwordHash) {
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

        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metrics.put("uptime", getUptime());

        // Request metrics
        Map<String, Object> req = new ConcurrentHashMap<>();
        req.put("total", totalRequests.get());
        req.put("failed", failedRequests.get());
        req.put("successRate", totalRequests.get() > 0 ? String.format("%.2f%%", (1.0 - (double)failedRequests.get()/totalRequests.get()) * 100) : "N/A");
        metrics.put("requests", req);

        // Storage metrics
        Map<String, Object> storage = new ConcurrentHashMap<>();
        storage.put("bytesUploaded", bytesUploaded.get());
        storage.put("bytesDownloaded", bytesDownloaded.get());
        long totalStorage = 0;
        long fileCount = 0;
        try {
            java.io.File root = new java.io.File(de.epax.StorageServerStart.getStoragePath());
            if (root.exists()) {
                totalStorage = calculateDirSize(root);
                fileCount = countFiles(root);
            }
        } catch (Exception e) {}
        storage.put("totalStorageBytes", totalStorage);
        storage.put("fileCount", fileCount);
        metrics.put("storage", storage);

        // System metrics
        Map<String, Object> sys = new ConcurrentHashMap<>();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        sys.put("heapUsedMB", (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()) / (1024 * 1024));
        sys.put("heapMaxMB", (ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()) / (1024 * 1024));
        sys.put("systemLoad", osBean.getSystemLoadAverage());
        sys.put("availableProcessors", osBean.getAvailableProcessors());
        metrics.put("system", sys);

        sendJson(exchange, 200, metrics);
    }

    private String getUptime() {
        RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
        long uptime = rb.getUptime();
        long days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(uptime);
        long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(uptime) % 24;
        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;
        return String.format("%d days, %d hours, %d minutes", days, hours, minutes);
    }

    private long calculateDirSize(java.io.File dir) {
        long size = 0;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) size += f.length();
                else if (f.isDirectory()) size += calculateDirSize(f);
            }
        }
        return size;
    }

    private long countFiles(java.io.File dir) {
        long count = 0;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) count++;
                else if (f.isDirectory()) count += countFiles(f);
            }
        }
        return count;
    }
}
