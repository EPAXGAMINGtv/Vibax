package de.epax.storageapi;

import java.io.Serializable;

public class ServerConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    public String ip;
    public String passwordHash;
    public String name;
    public volatile boolean online;
    public long lastCheck;
    public double avgLatency;
    public long freeSpace; // Free space in bytes
    public long totalSpace; // Total space in bytes
    public double cpuUsage; // CPU usage percentage
    public long memoryUsed; // Memory used in bytes
    public long memoryTotal; // Total memory in bytes
    public long lastSystemInfoUpdate; // Timestamp of last system info update

    public ServerConfig(String ip, String passwordHash) {
        this(ip, passwordHash, "Server-" + ip);
    }

    public ServerConfig(String ip, String passwordHash, String name) {
        this.ip = ip;
        this.passwordHash = passwordHash;
        this.name = name;
        this.online = false;
        this.lastCheck = 0;
        this.avgLatency = 0;
        this.freeSpace = -1;
        this.totalSpace = -1;
        this.cpuUsage = -1;
        this.memoryUsed = -1;
        this.memoryTotal = -1;
        this.lastSystemInfoUpdate = 0;
    }
}