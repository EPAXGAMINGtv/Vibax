package de.epax.storageapi.pool;

import de.epax.storageapi.ServerConfig;
import de.epax.storageapi.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class ServerPool {
    public enum LoadBalancingStrategy { ROUND_ROBIN, RANDOM, WEIGHTED, LEAST_CONNECTIONS }

    private final Map<Integer, ServerConfig> serverConfigs;
    private final Map<String, String> serverNameToUrl = new ConcurrentHashMap<>();
    private final LoadBalancingStrategy strategy;
    private final Map<String, Integer> connectionCounts = new ConcurrentHashMap<>();
    private int roundRobinIndex = 0;

    public ServerPool(Map<Integer, ServerConfig> servers) {
        this.serverConfigs = servers;
        this.strategy = LoadBalancingStrategy.ROUND_ROBIN;
        refresh();
    }

    public ServerPool(Map<Integer, ServerConfig> servers, LoadBalancingStrategy strategy) {
        this.serverConfigs = servers;
        this.strategy = strategy;
        refresh();
    }

    public void refresh() {
        serverNameToUrl.clear();
        for (Map.Entry<Integer, ServerConfig> entry : serverConfigs.entrySet()) {
            ServerConfig config = entry.getValue();
            serverNameToUrl.put(config.name, config.ip);
        }
    }

    public String getServer(LoadBalancingStrategy customStrategy) {
        List<String> available = getOnlineServers();
        if (available.isEmpty()) {
            throw new IllegalStateException("No servers available");
        }

        return switch (customStrategy != null ? customStrategy : strategy) {
            case ROUND_ROBIN -> getRoundRobin(available);
            case RANDOM -> getRandom(available);
            case WEIGHTED -> getWeighted(available);
            case LEAST_CONNECTIONS -> getLeastConnections(available);
        };
    }

    public String getServer() {
        return getServer(strategy);
    }

    public String getServer(String preferredServer) {
        if (preferredServer != null && serverNameToUrl.containsKey(preferredServer)) {
            ServerConfig config = findConfig(preferredServer);
            if (config != null && config.online) {
                return config.ip;
            }
        }
        return getServer();
    }

    public List<String> getServerNames() {
        return new ArrayList<>(serverNameToUrl.keySet());
    }

    public Map<String, String> getServerMap() {
        return new HashMap<>(serverNameToUrl);
    }

    public List<String> getOnlineServers() {
        List<String> online = new ArrayList<>();
        for (ServerConfig config : serverConfigs.values()) {
            if (config.online) {
                online.add(config.name);
            }
        }
        return online;
    }

    public Map<String, String> getAllServers() {
        Map<String, String> all = new HashMap<>();
        for (ServerConfig config : serverConfigs.values()) {
            all.put(config.name, config.ip);
        }
        return all;
    }

    private String getRoundRobin(List<String> servers) {
        String selected = servers.get(roundRobinIndex % servers.size());
        roundRobinIndex = (roundRobinIndex + 1) % servers.size();
        return selected;
    }

    private String getRandom(List<String> servers) {
        return servers.get(ThreadLocalRandom.current().nextInt(servers.size()));
    }

    private String getWeighted(List<String> servers) {
        // Simple weighted round-robin - in production, use real weights
        return getRoundRobin(servers);
    }

    private String getLeastConnections(List<String> servers) {
        return servers.stream()
                .min(Comparator.comparingInt(s -> connectionCounts.getOrDefault(s, 0)))
                .orElse(servers.get(0));
    }

    public void markConnectionStart(String server) {
        connectionCounts.merge(server, 1, Integer::sum);
    }

    public void markConnectionEnd(String server) {
        connectionCounts.merge(server, -1, (old, delta) -> Math.max(0, old + delta));
    }

    public int getConnectionCount(String server) {
        return connectionCounts.getOrDefault(server, 0);
    }

    public boolean ping(String serverName) {
        ServerConfig config = findConfig(serverName);
        if (config == null) return false;
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://" + config.ip + "/ion"))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private ServerConfig findConfig(String name) {
        for (ServerConfig config : serverConfigs.values()) {
            if (config.name.equals(name)) return config;
        }
        return null;
    }

    public void updateServerHealth(String serverName, boolean online) {
        ServerConfig config = findConfig(serverName);
        if (config != null) {
            config.online = online;
        }
    }
}
