package de.epax.storageapi.security;

import de.epax.storageapi.logging.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

public class IPFilter {
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    private final boolean whitelistEnabled;
    private final boolean blacklistEnabled;

    public IPFilter(boolean whitelistEnabled, boolean blacklistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
        this.blacklistEnabled = blacklistEnabled;
    }

    public void addWhitelist(String cidrOrIp) {
        whitelist.add(normalize(cidrOrIp));
    }

    public void addBlacklist(String cidrOrIp) {
        blacklist.add(normalize(cidrOrIp));
    }

    public void removeWhitelist(String cidrOrIp) {
        whitelist.remove(normalize(cidrOrIp));
    }

    public void removeBlacklist(String cidrOrIp) {
        blacklist.remove(normalize(cidrOrIp));
    }

    public boolean isAllowed(String ip) {
        // Blacklist takes precedence
        if (blacklistEnabled && isInList(ip, blacklist)) {
            Logger.warn("IP blocked by blacklist: " + ip);
            return false;
        }

        // If whitelist enabled, must be in whitelist
        if (whitelistEnabled) {
            boolean allowed = isInList(ip, whitelist);
            if (!allowed) {
                Logger.warn("IP not in whitelist: " + ip);
            }
            return allowed;
        }

        return true;
    }

    private boolean isInList(String ip, Set<String> list) {
        for (String entry : list) {
            if (entry.contains("/")) {
                // CIDR notation
                if (matchCidr(ip, entry)) return true;
            } else if (entry.equals("*")) {
                return true;
            } else if (entry.equals(ip)) {
                return true;
            } else if (entry.endsWith(".*")) {
                String prefix = entry.substring(0, entry.length() - 2);
                if (ip.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            String cidrIp = parts[0];
            int prefixLen = Integer.parseInt(parts[1]);

            InetAddress ipAddr = InetAddress.getByName(ip);
            InetAddress cidrAddr = InetAddress.getByName(cidrIp);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] cidrBytes = cidrAddr.getAddress();

            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != cidrBytes[i]) return false;
            }

            if (remainingBits > 0) {
                int mask = 0xFF00 << (8 - remainingBits);
                int ipByte = (ipBytes[fullBytes] << 8) & 0xFF00;
                int cidrByte = (cidrBytes[fullBytes] << 8) & 0xFF00;
                if ((ipByte & mask) != (cidrByte & mask)) return false;
            }

            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String normalize(String s) {
        return s.trim().toLowerCase();
    }

    public void clear() {
        whitelist.clear();
        blacklist.clear();
    }

    public int getWhitelistSize() {
        return whitelist.size();
    }

    public int getBlacklistSize() {
        return blacklist.size();
    }
}
