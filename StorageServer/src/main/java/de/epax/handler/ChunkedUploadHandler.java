package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.*;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SECURITY FIX:
 *  - Added per-chunk size limit (max 10 MB per chunk)
 *  - Added total session size limit (max 2 GB across all chunks)
 *  - Sessions auto-expire after 30 minutes to prevent memory exhaustion
 *  - readAllBytes() replaced with bounded read to prevent OOM
 */
public class ChunkedUploadHandler extends JsonHandler implements HttpHandler {

    private static final long MAX_CHUNK_BYTES   = 10L  * 1024 * 1024;  // 10 MB per chunk
    private static final long MAX_TOTAL_BYTES   = 2000L * 1024 * 1024; // 2 GB total
    private static final long SESSION_TTL_MS    = 30L  * 60 * 1000;    // 30 minutes

    private static final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public ChunkedUploadHandler(String passwordHash) {
        super(passwordHash);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        purgeExpiredSessions();

        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();

        if (query == null) {
            sendJson(exchange, 400, Map.of("error", "Missing parameters"));
            return;
        }

        Map<String, String> params = parseQuery(query);
        String path         = params.get("path");
        String sessionId    = params.get("sessionId");
        String chunkIndex   = params.get("chunk");
        String totalChunks  = params.get("totalChunks");
        String action       = params.getOrDefault("action", "upload");

        try {
            switch (action) {
                case "init"     -> handleInit(exchange, path, totalChunks);
                case "upload"   -> handleChunk(exchange, sessionId, chunkIndex);
                case "complete" -> handleComplete(exchange, sessionId);
                case "status"   -> handleStatus(exchange, sessionId);
                default         -> sendJson(exchange, 400, Map.of("error", "Unknown action"));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleInit(HttpExchange exchange, String path, String totalChunksStr) throws IOException {
        if (path == null || path.isBlank()) {
            sendJson(exchange, 400, Map.of("error", "Missing path"));
            return;
        }
        String sessionId  = UUID.randomUUID().toString();
        int totalChunks   = totalChunksStr != null ? Integer.parseInt(totalChunksStr) : 1;
        sessions.put(sessionId, new UploadSession(sessionId, path, totalChunks));
        sendJson(exchange, 200, Map.of("sessionId", sessionId, "totalChunks", totalChunks, "status", "initialized"));
    }

    private void handleChunk(HttpExchange exchange, String sessionId, String chunkIdxStr) throws IOException {
        if (sessionId == null || chunkIdxStr == null) {
            sendJson(exchange, 400, Map.of("error", "Missing sessionId or chunk"));
            return;
        }
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            sendJson(exchange, 404, Map.of("error", "Session not found"));
            return;
        }

        int chunkIndex = Integer.parseInt(chunkIdxStr);

        // SECURITY FIX: Bounded read — never call readAllBytes() without a limit
        byte[] chunkData;
        try (InputStream in = exchange.getRequestBody()) {
            chunkData = readBounded(in, MAX_CHUNK_BYTES);
        }

        // SECURITY FIX: Enforce total upload size
        if (session.getTotalBytes() + chunkData.length > MAX_TOTAL_BYTES) {
            sessions.remove(sessionId);
            sendJson(exchange, 413, Map.of("error", "Total upload size exceeds limit of " + MAX_TOTAL_BYTES + " bytes"));
            return;
        }

        session.addChunk(chunkIndex, chunkData);

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("chunk", chunkIndex);
        resp.put("received", session.getReceivedChunks());
        resp.put("total", session.totalChunks);
        resp.put("progress", (int)(session.getReceivedChunks() * 100.0 / session.totalChunks));
        sendJson(exchange, 200, resp);
    }

    private void handleComplete(HttpExchange exchange, String sessionId) throws IOException {
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            sendJson(exchange, 404, Map.of("error", "Session not found"));
            return;
        }
        if (!session.isComplete()) {
            sendJson(exchange, 400, Map.of("error", "Upload incomplete",
                    "received", session.getReceivedChunks(), "total", session.totalChunks));
            return;
        }

        File outFile = FileManager.resolveSafePath(session.path);
        File parent  = outFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outFile);
             FileChannel channel = fos.getChannel()) {
            for (int i = 0; i < session.totalChunks; i++) {
                byte[] chunk = session.getChunk(i);
                if (chunk != null) channel.write(java.nio.ByteBuffer.wrap(chunk));
            }
        }

        sessions.remove(sessionId);
        sendJson(exchange, 200, Map.of("status", "complete", "path", session.path, "size", outFile.length()));
    }

    private void handleStatus(HttpExchange exchange, String sessionId) throws IOException {
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            sendJson(exchange, 404, Map.of("error", "Session not found"));
            return;
        }
        sendJson(exchange, 200, Map.of(
                "sessionId", sessionId,
                "received",  session.getReceivedChunks(),
                "total",     session.totalChunks,
                "progress",  (int)(session.getReceivedChunks() * 100.0 / session.totalChunks),
                "complete",  session.isComplete()
        ));
    }

    /** Read at most maxBytes from stream; throws IOException if limit exceeded. */
    private byte[] readBounded(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(tmp)) != -1) {
            total += n;
            if (total > maxBytes) throw new IOException("Chunk exceeds max size of " + maxBytes + " bytes");
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    /** Remove sessions older than SESSION_TTL_MS to prevent memory leaks. */
    private void purgeExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> now - e.getValue().createdAt > SESSION_TTL_MS);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new ConcurrentHashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key   = decodeURL(pair.substring(0, idx));
                String value = decodeURL(pair.substring(idx + 1));
                result.put(key, value);
            }
        }
        return result;
    }

    private String decodeURL(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static class UploadSession {
        final String sessionId;
        final String path;
        final int    totalChunks;
        final long   createdAt = System.currentTimeMillis();
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();

        UploadSession(String sessionId, String path, int totalChunks) {
            this.sessionId   = sessionId;
            this.path        = path;
            this.totalChunks = totalChunks;
        }

        synchronized void addChunk(int index, byte[] data)  { chunks.put(index, data); }
        synchronized byte[] getChunk(int index)              { return chunks.get(index); }
        synchronized int getReceivedChunks()                 { return chunks.size(); }
        synchronized boolean isComplete()                    { return getReceivedChunks() >= totalChunks; }
        synchronized long getTotalBytes() {
            return chunks.values().stream().mapToLong(b -> b.length).sum();
        }
    }
}
