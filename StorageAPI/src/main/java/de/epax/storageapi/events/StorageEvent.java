package de.epax.storageapi.events;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class StorageEvent {
    public enum EventType {
        SERVER_UP, SERVER_DOWN,
        FILE_CREATED, FILE_DELETED, FILE_MODIFIED, FILE_RENAMED, FILE_COPIED, FILE_MOVED,
        DIRECTORY_CREATED, DIRECTORY_DELETED, DIRECTORY_MODIFIED,
        VERSION_CREATED, VERSION_RESTORED,
        LOCK_ACQUIRED, LOCK_RELEASED,
        METADATA_SET, METADATA_REMOVED,
        CONFIG_CHANGED,
        ERROR_OCCURRED
    }

    private final EventType type;
    private final String serverName;
    private final String path;
    private final String oldPath;
    private final Map<String, Object> data;
    private final LocalDateTime timestamp;

    private StorageEvent(Builder builder) {
        this.type = builder.type;
        this.serverName = builder.serverName;
        this.path = builder.path;
        this.oldPath = builder.oldPath;
        this.data = new HashMap<>(builder.data);
        this.timestamp = LocalDateTime.now();
    }

    public EventType getType() { return type; }
    public String getServerName() { return serverName; }
    public String getPath() { return path; }
    public String getOldPath() { return oldPath; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Object getData(String key) { return data.get(key); }

    public static Builder builder(EventType type) {
        return new Builder(type);
    }

    public static StorageEvent serverUp(String serverName) {
        return builder(EventType.SERVER_UP).serverName(serverName).build();
    }

    public static StorageEvent serverDown(String serverName) {
        return builder(EventType.SERVER_DOWN).serverName(serverName).build();
    }

    public static StorageEvent fileCreated(String serverName, String path) {
        return builder(EventType.FILE_CREATED).serverName(serverName).path(path).build();
    }

    public static StorageEvent fileDeleted(String serverName, String path) {
        return builder(EventType.FILE_DELETED).serverName(serverName).path(path).build();
    }

    public static StorageEvent fileModified(String serverName, String path) {
        return builder(EventType.FILE_MODIFIED).serverName(serverName).path(path).build();
    }

    public static StorageEvent fileRenamed(String serverName, String oldPath, String newPath) {
        return builder(EventType.FILE_RENAMED).serverName(serverName).path(newPath).oldPath(oldPath).build();
    }

    public static StorageEvent fileCopied(String serverName, String source, String target) {
        return builder(EventType.FILE_COPIED).serverName(serverName).path(target)
                .data("source", source).build();
    }

    public static StorageEvent fileMoved(String serverName, String source, String target) {
        return builder(EventType.FILE_MOVED).serverName(serverName).path(target)
                .data("source", source).build();
    }

    public static StorageEvent versionCreated(String serverName, String path) {
        return builder(EventType.VERSION_CREATED).serverName(serverName).path(path).build();
    }

    public static StorageEvent lockAcquired(String serverName, String path, String owner) {
        return builder(EventType.LOCK_ACQUIRED).serverName(serverName).path(path)
                .data("owner", owner).build();
    }

    public static StorageEvent errorOccurred(String serverName, String message, Throwable error) {
        return builder(EventType.ERROR_OCCURRED).serverName(serverName)
                .data("message", message)
                .data("exception", error != null ? error.getClass().getSimpleName() : "null")
                .build();
    }

    public static class Builder {
        private final EventType type;
        private String serverName = "";
        private String path = "";
        private String oldPath = "";
        private Map<String, Object> data = new HashMap<>();

        public Builder(EventType type) {
            this.type = type;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder oldPath(String oldPath) {
            this.oldPath = oldPath;
            return this;
        }

        public Builder data(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data.putAll(data);
            return this;
        }

        public StorageEvent build() {
            return new StorageEvent(this);
        }
    }

    @Override
    public String toString() {
        return String.format("StorageEvent{type=%s, server='%s', path='%s', time='%s'}",
                type, serverName, path, timestamp);
    }
}
