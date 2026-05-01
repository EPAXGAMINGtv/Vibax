package de.epax.storageapi.batch;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchRequest {
    private final List<BatchOperation> operations = Collections.synchronizedList(new ArrayList<>());
    private final String batchId;
    private final boolean stopOnError;

    public BatchRequest() {
        this(UUID.randomUUID().toString(), false);
    }

    public BatchRequest(String batchId, boolean stopOnError) {
        this.batchId = batchId;
        this.stopOnError = stopOnError;
    }

    public BatchRequest addOperation(BatchOperation operation) {
        operations.add(operation);
        return this;
    }

    public BatchRequest addRead(String server, String path) {
        operations.add(new BatchOperation(UUID.randomUUID().toString(), "read", Map.of("server", server, "path", path)));
        return this;
    }

    public BatchRequest addWrite(String server, String path, String content) {
        operations.add(new BatchOperation(UUID.randomUUID().toString(), "write", Map.of("server", server, "path", path, "content", content)));
        return this;
    }

    public BatchRequest addList(String server, String path) {
        operations.add(new BatchOperation(UUID.randomUUID().toString(), "list", Map.of("server", server, "path", path)));
        return this;
    }

    public BatchRequest addDelete(String server, String path) {
        operations.add(new BatchOperation(UUID.randomUUID().toString(), "delete", Map.of("server", server, "path", path)));
        return this;
    }

    public BatchRequest addExists(String server, String path) {
        operations.add(new BatchOperation(UUID.randomUUID().toString(), "exists", Map.of("server", server, "path", path)));
        return this;
    }

    public List<BatchOperation> getOperations() {
        return Collections.unmodifiableList(operations);
    }

    public int size() {
        return operations.size();
    }

    public String getBatchId() {
        return batchId;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public static class BatchOperation {
        private final String id;
        private final String type;
        private final Map<String, Object> params;

        public BatchOperation(String id, String type, Map<String, Object> params) {
            this.id = id;
            this.type = type;
            this.params = new HashMap<>(params);
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public Map<String, Object> getParams() { return Collections.unmodifiableMap(params); }
    }
}
