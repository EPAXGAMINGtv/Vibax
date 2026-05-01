package de.epax.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.file.FileManager;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkedUploadHandler extends JsonHandler implements HttpHandler {

    // In-memory tracking of upload sessions (in production use Redis or DB)
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

        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();

        if (query == null) {
            sendJson(exchange, 400, Map.of("error", "Missing parameters"));
            return;
        }

        Map<String, String> params = parseQuery(query);
        String path = params.get("path");
        String sessionId = params.get("sessionId");
        String chunkIndex = params.get("chunk");
        String totalChunks = params.get("totalChunks");
        String action = params.get("action");

        if (action == null) action = "upload";

        try {
            if ("init".equals(action)) {
                handleInit(exchange, path, totalChunks);
            } else if ("upload".equals(action)) {
                handleChunk(exchange, path, sessionId, chunkIndex, params);
            } else if ("complete".equals(action)) {
                handleComplete(exchange, sessionId);
            } else if ("status".equals(action)) {
                handleStatus(exchange, sessionId);
            } else {
                sendJson(exchange, 400, Map.of("error", "Unknown action"));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", e.getMessage()));
        }
    }

    private void handleInit(HttpExchange exchange, String path, String totalChunksStr) throws IOException {
        String sessionId = generateSessionId();
        int totalChunks = totalChunksStr != null ? Integer.parseInt(totalChunksStr) : 1;
        UploadSession session = new UploadSession(sessionId, path, totalChunks);
        sessions.put(sessionId, session);
        sendJson(exchange, 200, Map.of(
                "sessionId", sessionId,
                "totalChunks", totalChunks,
                "status", "initialized"
        ));
    }

    private void handleChunk(HttpExchange exchange, String path, String sessionId, String chunkIdxStr, Map<String, String> params) throws IOException {
        if (sessionId == null || chunkIdxStr == null) {
            sendJson(exchange, 400, Map.of("error", "Missing sessionId or chunk"));
            return;
        }

        int chunkIndex = Integer.parseInt(chunkIdxStr);
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            sendJson(exchange, 404, Map.of("error", "Session not found"));
            return;
        }

        // Read chunk data
        try (InputStream in = exchange.getRequestBody()) {
            byte[] chunkData = in.readAllBytes();
            session.addChunk(chunkIndex, chunkData);
        }

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
            sendJson(exchange, 400, Map.of("error", "Upload incomplete", "received", session.getReceivedChunks(), "total", session.totalChunks));
            return;
        }

        // Assemble file
        File outFile = FileManager.resolveSafePath(session.path);
        File parent = outFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();

        try (FileOutputStream fos = new FileOutputStream(outFile);
             FileChannel channel = fos.getChannel()) {
            for (int i = 0; i < session.totalChunks; i++) {
                byte[] chunk = session.getChunk(i);
                if (chunk != null) {
                    channel.write(java.nio.ByteBuffer.wrap(chunk));
                }
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
                "received", session.getReceivedChunks(),
                "total", session.totalChunks,
                "progress", (int)(session.getReceivedChunks() * 100.0 / session.totalChunks),
                "complete", session.isComplete()
        ));
    }

    private String generateSessionId() {
        return java.util.UUID.randomUUID().toString();
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new ConcurrentHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = decodeURL(pair.substring(0, idx));
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
        final int totalChunks;
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();

        UploadSession(String sessionId, String path, int totalChunks) {
            this.sessionId = sessionId;
            this.path = path;
            this.totalChunks = totalChunks;
        }

        synchronized void addChunk(int index, byte[] data) {
            chunks.put(index, data);
        }

        synchronized byte[] getChunk(int index) {
            return chunks.get(index);
        }

        synchronized int getReceivedChunks() {
            return chunks.size();
        }

        synchronized boolean isComplete() {
            return getReceivedChunks() >= totalChunks;
        }
    }
}
