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
    }
}