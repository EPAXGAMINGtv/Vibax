package de.epax.storageapi.security;

import de.epax.storageapi.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RBAC {
    public enum Permission {
        // File operations
        FILE_READ, FILE_WRITE, FILE_DELETE, FILE_UPLOAD, FILE_DOWNLOAD, FILE_CREATE, FILE_RENAME, FILE_COPY, FILE_MOVE,
        // Directory operations
        DIR_LIST, DIR_CREATE, DIR_DELETE,
        // Versioning
        VERSION_CREATE, VERSION_RESTORE,
        // Locking
        LOCK_ACQUIRE, LOCK_RELEASE,
        // Metadata
        METADATA_READ, METADATA_WRITE,
        // Admin
        ADMIN_ALL, SERVER_STATUS, CONFIG_READ, CONFIG_WRITE,
        // Search
        SEARCH
    }

    public static class Role {
        private final String name;
        private final Set<Permission> permissions;
        private final Set<String> allowedPaths; // Path restrictions (regex patterns)
        private final int maxFileSize;
        private final int maxQuota;

        public Role(String name, Set<Permission> permissions, Set<String> allowedPaths, int maxFileSize, int maxQuota) {
            this.name = name;
            this.permissions = new HashSet<>(permissions);
            this.allowedPaths = allowedPaths != null ? new HashSet<>(allowedPaths) : Set.of("*");
            this.maxFileSize = maxFileSize;
            this.maxQuota = maxQuota;
        }

        public boolean hasPermission(Permission perm) {
            if (permissions.contains(Permission.ADMIN_ALL)) return true;
            return permissions.contains(perm);
        }

        public boolean canAccessPath(String path) {
            if (allowedPaths.contains("*")) return true;
            return allowedPaths.stream().anyMatch(pattern -> {
                if (pattern.endsWith("/*")) {
                    String prefix = pattern.substring(0, pattern.length() - 2);
                    return path.startsWith(prefix);
                } else if (pattern.contains("*")) {
                    // Simple glob -> regex
                    String regex = pattern.replace(".", "\\.").replace("*", ".*");
                    return path.matches(regex);
                } else {
                    return path.equals(pattern);
                }
            });
        }

        public boolean checkFileSizeLimit(long size) {
            return maxFileSize <= 0 || size <= maxFileSize;
        }

        public int getMaxFileSize() { return maxFileSize; }
        public int getMaxQuota() { return maxQuota; }
        public String getName() { return name; }
    }

    private static final Map<String, Role> roleDefinitions = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> userRoles = new ConcurrentHashMap<>();

    static {
        // Define default roles
        defineRole("ADMIN", new HashSet<>(Arrays.asList(Permission.values())), Set.of("*"), -1, -1);
        defineRole("USER", Set.of(
                Permission.FILE_READ, Permission.FILE_WRITE, Permission.FILE_DELETE,
                Permission.FILE_UPLOAD, Permission.FILE_DOWNLOAD, Permission.FILE_CREATE,
                Permission.FILE_RENAME, Permission.FILE_COPY, Permission.FILE_MOVE,
                Permission.DIR_LIST, Permission.DIR_CREATE, Permission.DIR_DELETE,
                Permission.VERSION_CREATE, Permission.LOCK_ACQUIRE, Permission.LOCK_RELEASE,
                Permission.METADATA_READ, Permission.METADATA_WRITE,
                Permission.SEARCH
        ), Set.of("/home/*", "/uploads/*"), 100 * 1024 * 1024, 1024 * 1024 * 1024); // 100MB per file, 1GB total

        defineRole("READONLY", Set.of(
                Permission.FILE_READ, Permission.FILE_DOWNLOAD, Permission.DIR_LIST, Permission.SEARCH
        ), Set.of("/readonly/*"), 50 * 1024 * 1024, 500 * 1024 * 1024);
    }

    public static void defineRole(String roleName, Set<Permission> permissions, Set<String> allowedPaths, int maxFileSize, int maxQuota) {
        roleDefinitions.put(roleName.toUpperCase(), new Role(roleName.toUpperCase(), permissions, allowedPaths, maxFileSize, maxQuota));
    }

    public static void assignRole(String username, String roleName) {
        userRoles.computeIfAbsent(username, k -> ConcurrentHashMap.newKeySet()).add(roleName.toUpperCase());
        Logger.info("Assigned role " + roleName + " to user " + username);
    }

    public static void revokeRole(String username, String roleName) {
        Set<String> r = userRoles.get(username);
        if (r != null) {
            r.remove(roleName.toUpperCase());
            if (r.isEmpty()) userRoles.remove(username);
        }
    }

    public static boolean checkPermission(String username, Permission permission) {
        Set<String> roles = getUserRoles(username);
        for (String roleName : roles) {
            Role role = roleDefinitions.get(roleName);
            if (role != null && role.hasPermission(permission)) return true;
        }
        return false;
    }

    public static boolean checkPathAccess(String username, String path) {
        Set<String> roles = getUserRoles(username);
        for (String roleName : roles) {
            Role role = roleDefinitions.get(roleName);
            if (role != null && role.canAccessPath(path)) return true;
        }
        return false;
    }

    public static boolean checkFileSize(String username, long size) {
        Set<String> roles = getUserRoles(username);
        for (String roleName : roles) {
            Role role = roleDefinitions.get(roleName);
            if (role != null && !role.checkFileSizeLimit(size)) return false;
        }
        return true;
    }

    public static Set<String> getUserRoles(String username) {
        return userRoles.getOrDefault(username, Set.of());
    }

    public static List<Role> getEffectiveRoles(String username) {
        Set<String> roleNames = getUserRoles(username);
        return roleNames.stream()
                .map(roleDefinitions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static Role getRole(String roleName) {
        return roleDefinitions.get(roleName.toUpperCase());
    }

    public static Set<String> getAllRoleNames() {
        return new HashSet<>(roleDefinitions.keySet());
    }
}
