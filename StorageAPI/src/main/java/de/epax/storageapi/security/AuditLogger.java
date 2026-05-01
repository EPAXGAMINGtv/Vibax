package de.epax.storageapi.security;

import de.epax.storageapi.logging.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AuditLogger {
    private static final String AUDIT_LOG_FILE = "audit.log";
    private static final BlockingQueue<AuditEvent> eventQueue = new LinkedBlockingQueue<>(10000);
    private static final ScheduledExecutorService logger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AuditLogger");
        t.setDaemon(true);
        return t;
    });
    private static final List<Consumer<AuditEvent>> listeners = new CopyOnWriteArrayList<>();
    private static volatile boolean running = false;
    private static final AtomicInteger droppedEvents = new AtomicInteger(0);

    public enum AuditAction {
        LOGIN, LOGOUT, TOKEN_CREATED, TOKEN_REVOKED,
        FILE_READ, FILE_WRITE, FILE_UPLOAD, FILE_DOWNLOAD, FILE_DELETE, FILE_CREATE, FILE_RENAME, FILE_COPY, FILE_MOVE,
        DIRECTORY_CREATE, DIRECTORY_DELETE, DIRECTORY_LIST,
        VERSION_CREATE, VERSION_RESTORE,
        LOCK_ACQUIRE, LOCK_RELEASE,
        METADATA_SET, METADATA_DELETE,
        CONFIG_CHANGE, ADMIN_ACTION,
        AUTH_FAILURE, UNAUTHORIZED_ACCESS,
        SERVER_UP, SERVER_DOWN
    }

    public static class AuditEvent {
        final AuditAction action;
        final String username;
        final String server;
        final String path;
        final String clientIp;
        final String userAgent;
        final LocalDateTime timestamp;
        final Map<String, Object> details;

        public AuditEvent(AuditAction action, String username, String server, String path, String clientIp, String userAgent, Map<String, Object> details) {
            this.action = action;
            this.username = username;
            this.server = server;
            this.path = path;
            this.clientIp = clientIp;
            this.userAgent = userAgent;
            this.timestamp = LocalDateTime.now();
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s user=%s server=%s path=%s ip=%s details=%s",
                    timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    action, username, server, path, clientIp, details);
        }
    }

    public static void start() {
        if (running) return;
        running = true;
        logger.scheduleAtFixedRate(AuditLogger::flushEvents, 1, 1, TimeUnit.SECONDS);
        Logger.info("AuditLogger started");
    }

    public static void stop() {
        running = false;
        flushEvents();
        logger.shutdown();
        Logger.info("AuditLogger stopped. Dropped events: " + droppedEvents.get());
    }

    public static void log(AuditEvent event) {
        if (!running) return;
        if (!eventQueue.offer(event)) {
            droppedEvents.incrementAndGet();
            Logger.warn("Audit event queue full, event dropped: " + event.action);
        }
        // Notify listeners
        listeners.forEach(l -> {
            try { l.accept(event); } catch (Exception e) { /* ignore */ }
        });
    }

    private static void flushEvents() {
        List<AuditEvent> batch = new ArrayList<>();
        eventQueue.drainTo(batch, 100);
        if (!batch.isEmpty()) {
            writeToLog(batch);
        }
    }

    private static void writeToLog(List<AuditEvent> events) {
        // Write to file and also to system log
        try (java.io.FileWriter fw = new java.io.FileWriter(AUDIT_LOG_FILE, true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw)) {
            for (AuditEvent e : events) {
                bw.write(e.toString());
                bw.newLine();
                Logger.debug("[AUDIT] " + e.action + " by " + e.username + " on " + e.path);
            }
        } catch (IOException ex) {
            Logger.error("Failed to write audit log: " + ex.getMessage());
        }
    }

    public static void addListener(Consumer<AuditEvent> listener) {
        listeners.add(listener);
    }

    public static void removeListener(Consumer<AuditEvent> listener) {
        listeners.remove(listener);
    }

    public static int getPendingEvents() {
        return eventQueue.size();
    }

    public static int getDroppedEvents() {
        return droppedEvents.get();
    }
}
