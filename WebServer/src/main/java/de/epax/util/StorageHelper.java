package de.epax.util;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Helper for reading/writing data across all configured StorageServers.
 */
public final class StorageHelper {

    private StorageHelper() {}

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

    public static boolean write(String path, String content) {
        boolean any = false;
        for (String server : StorageAPI.getServerNames()) {
            Map<String, Object> health = StorageAPI.getServerHealth(server);
            if (health != null && Boolean.TRUE.equals(health.get("online"))) {
                if (StorageAPI.writeFile(server, path, content)) {
                    any = true;
                    Logger.info("Written to " + server + ": " + path);
                }
            }
        }
        return any;
    }

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

    public static List<String> listFiles(String directory) {
        String server = getOnlineServer();
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
