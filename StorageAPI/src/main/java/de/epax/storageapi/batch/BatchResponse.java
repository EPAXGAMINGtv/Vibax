package de.epax.storageapi.batch;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BatchResponse {
    private final Map<String, Object> successResults = new ConcurrentHashMap<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private final List<String> order = Collections.synchronizedList(new ArrayList<>());

    public void addSuccess(String operationId, Object result) {
        successResults.put(operationId, result);
        order.add(operationId);
    }

    public void addFailure(String operationId, String error) {
        failures.put(operationId, error);
        order.add(operationId);
    }

    public Object getResult(String operationId) {
        return successResults.get(operationId);
    }

    public String getError(String operationId) {
        return failures.get(operationId);
    }

    public boolean isSuccess(String operationId) {
        return successResults.containsKey(operationId);
    }

    public boolean isFailure(String operationId) {
        return failures.containsKey(operationId);
    }

    public Map<String, Object> getAllSuccesses() {
        return Collections.unmodifiableMap(successResults);
    }

    public Map<String, String> getAllFailures() {
        return Collections.unmodifiableMap(failures);
    }

    public int getSuccessCount() {
        return successResults.size();
    }

    public int getFailureCount() {
        return failures.size();
    }

    public int getTotalCount() {
        return order.size();
    }

    public List<String> getOrder() {
        return Collections.unmodifiableList(order);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public String toSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Batch Response: ").append(getSuccessCount()).append(" succeeded, ")
          .append(getFailureCount()).append(" failed\n");
        for (String id : order) {
            if (successResults.containsKey(id)) {
                sb.append("  [").append(id).append("] SUCCESS: ").append(successResults.get(id)).append("\n");
            } else {
                sb.append("  [").append(id).append("] FAILED: ").append(failures.get(id)).append("\n");
            }
        }
        return sb.toString();
    }
}
