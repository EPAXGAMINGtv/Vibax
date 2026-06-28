package de.epax.util;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;

import java.util.*;

/**
 * Helper for reading/writing data across all configured StorageServers.
 * Writes go to the least-utilized server; reads/searching dynamically
 * check all online servers.
 */
public final class StorageHelper {

    private StorageHelper() {}

    /**
     * Returns the name of the least-utilized online server based on a
     * composite score of free space, CPU, and memory usage.
     * Falls back to any online server if health data is unavailable.
     */
    public static String getLeastUsedServer() {
        String best = null;
        double bestScore = Double.MAX_VALUE;

        for (String name : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(name);
            if (health == null || !Boolean.TRUE.equals(health.get("online"))) continue;

            long freeSpace   = ((Number) health.getOrDefault("freeSpace", -1L)).longValue();
            long totalSpace  = ((Number) health.getOrDefault("totalSpace", -1L)).longValue();
            double cpuUsage  = ((Number) health.getOrDefault("cpuUsage", -1.0)).doubleValue();
            long memoryUsed  = ((Number) health.getOrDefault("memoryUsed", -1L)).longValue();
            long memoryTotal = ((Number) health.getOrDefault("memoryTotal", -1L)).longValue();

            double spaceScore = totalSpace > 0 ? 1.0 - (double) freeSpace / totalSpace : 0.5;
            double cpuScore   = cpuUsage >= 0 ? cpuUsage : 0.5;
            double memScore   = memoryTotal > 0 ? (double) memoryUsed / memoryTotal : 0.5;
            double score      = spaceScore * 0.5 + cpuScore * 0.25 + memScore * 0.25;

            if (score < bestScore) {
                bestScore = score;
                best = name;
            }
        }
        return best;
    }

    public static byte[] readBytes(String path) {
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                byte[] content = StorageAPI.downloadFile(server, path);
                if (content != null) return content;
            }
        }
        return null;
    }

    public static String getOnlineServer() {
        for (String name : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(name);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                return name;
            }
        }
        return null;
    }

    public static String read(String path) {
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                String content = StorageAPI.readFile(server, path);
                if (content != null) return content;
            }
        }
        return null;
    }

    public static String readNoCache(String path) {
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                String content = StorageAPI.readFile(server, path, false);
                if (content != null) return content;
            }
        }
        return null;
    }

    /**
     * Writes content only to the least-utilized server (not replicated).
     * Falls back to any online server if no health info is available.
     * Clears entire cache to prevent stale reads across servers.
     */
    public static boolean write(String path, String content) {
        String target = getLeastUsedServer();
        if (target == null) target = getOnlineServer();
        if (target == null) return false;

        if (StorageAPI.writeFile(target, path, content)) {
            StorageAPI.clearCache();
            Logger.info("Written to " + target + ": " + path);
            return true;
        }
        Logger.warn("Write failed to " + target + ": " + path);
        return false;
    }

    /**
     * Deletes from all online servers (files may be on any server).
     */
    public static boolean delete(String path) {
        boolean any = false;
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                if (StorageAPI.deleteFile(server, path)) any = true;
            }
        }
        return any;
    }

    public static boolean mkdir(String path) {
        String server = getOnlineServer();
        return server != null && StorageAPI.mkdir(server, path);
    }

    /**
     * Lists files on the least-utilized server.
     */
    public static List<String> listFiles(String directory) {
        String server = getLeastUsedServer();
        if (server == null) server = getOnlineServer();
        if (server == null) return List.of();
        List<String> files = StorageAPI.listFiles(server, directory);
        return files != null ? files : List.of();
    }

    public static boolean exists(String path) {
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                if (StorageAPI.exists(server, path)) return true;
            }
        }
        return false;
    }
}
