package de.epax.file;

import de.epax.StorageServerStart;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

public class FileManager {

    private static String storedFilesPath;
    private static final Map<String, FileLock> fileLocks = new ConcurrentHashMap<>();
    private static final Map<String, FileVersion> fileVersions = new ConcurrentHashMap<>();
    private static final Map<String, FileMetadata> fileMetadata = new ConcurrentHashMap<>();
    private static final int MAX_VERSIONS = 10;

    public FileManager() {
        storedFilesPath = StorageServerStart.getStoragePath();
        File root = new File(storedFilesPath);
        if (!root.exists()) root.mkdirs();
    }

    public static File resolveSafePath(String relativePath) throws IOException {
        if (relativePath == null) throw new IOException("Path cannot be null");
        relativePath = relativePath.trim();
        if (relativePath.equals("/") || relativePath.isEmpty()) relativePath = "";
        File base = new File(storedFilesPath).getCanonicalFile();
        File target = new File(base, relativePath).getCanonicalFile();
        if (!target.getPath().startsWith(base.getPath())) {
            throw new SecurityException("Path traversal attack detected: " + relativePath);
        }
        return target;
    }

    public static List<String> list(String relativePath) {
        List<String> result = new ArrayList<>();
        try {
            File target = resolveSafePath(relativePath);
            File[] list = target.listFiles();
            if (list == null) return result;
            for (File f : list) result.add(f.getName());
        } catch (Exception e) {
            // Return empty list on error
        }
        return result;
    }

    public static boolean createDirectory(String relativePath) {
        try {
            File dir = resolveSafePath(relativePath);
            if (!dir.exists()) return dir.mkdirs();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean createFile(String relativePath) {
        try {
            File file = resolveSafePath(relativePath);
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();
            if (!file.exists()) return file.createNewFile();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static String readFile(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    public static void writeFile(String relativePath, String content) throws IOException {
        File file = resolveSafePath(relativePath);
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static boolean delete(String relativePath) {
        try {
            File f = resolveSafePath(relativePath);
            return deleteRecursively(f);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }

    public static boolean isDirectory(String relativePath) {
        try {
            File f = resolveSafePath(relativePath);
            return f.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean exists(String relativePath) {
        try {
            File f = resolveSafePath(relativePath);
            return f.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static long getFileSize(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        return file.length();
    }

    public static long getDirectorySize(String relativePath) throws IOException {
        File dir = resolveSafePath(relativePath);
        if (!dir.exists() || !dir.isDirectory()) throw new IOException("Directory not found");
        return calculateDirectorySize(dir);
    }

    private static long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    public static boolean copyFile(String sourcePath, String targetPath) throws IOException {
        File source = resolveSafePath(sourcePath);
        File target = resolveSafePath(targetPath);
        if (!source.exists() || !source.isFile()) throw new IOException("Source file not found");
        File parent = target.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public static boolean moveFile(String sourcePath, String targetPath) throws IOException {
        File source = resolveSafePath(sourcePath);
        File target = resolveSafePath(targetPath);
        if (!source.exists()) throw new IOException("Source not found");
        File parent = target.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public static boolean copyDirectory(String sourcePath, String targetPath) throws IOException {
        File source = resolveSafePath(sourcePath);
        File target = resolveSafePath(targetPath);
        if (!source.exists() || !source.isDirectory()) throw new IOException("Source directory not found");
        copyDirectoryRecursively(source, target);
        return true;
    }

    private static void copyDirectoryRecursively(File source, File target) throws IOException {
        if (!target.exists()) {
            if (!target.mkdirs()) throw new IOException("Failed to create directory: " + target);
        }
        File[] children = source.listFiles();
        if (children != null) {
            for (File child : children) {
                File dest = new File(target, child.getName());
                if (child.isFile()) {
                    Files.copy(child.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else if (child.isDirectory()) {
                    copyDirectoryRecursively(child, dest);
                }
            }
        }
    }

    public static boolean moveDirectory(String sourcePath, String targetPath) throws IOException {
        File source = resolveSafePath(sourcePath);
        File target = resolveSafePath(targetPath);
        if (!source.exists() || !source.isDirectory()) throw new IOException("Source directory not found");
        File parent = target.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public static boolean rename(String relativePath, String newName) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists()) throw new IOException("File not found");
        File newFile = new File(file.getParentFile(), newName);
        return file.renameTo(newFile);
    }

    public static List<String> listRecursive(String relativePath) {
        List<String> result = new ArrayList<>();
        try {
            File dir = resolveSafePath(relativePath);
            if (!dir.isDirectory()) return result;
            collectFilesRecursive(dir, relativePath, result);
        } catch (Exception e) {
        }
        return result;
    }

    private static void collectFilesRecursive(File dir, String currentPath, List<String> result) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String relPath = currentPath.equals("/") ? "/" + file.getName() : currentPath + "/" + file.getName();
                result.add(relPath);
                if (file.isDirectory()) {
                    collectFilesRecursive(file, relPath, result);
                }
            }
        }
    }

    public static String calculateChecksum(String relativePath, String algorithm) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            try (FileInputStream fis = new FileInputStream(file);
                 DigestInputStream dis = new DigestInputStream(fis, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) { /* digest updates automatically */ }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Unsupported checksum algorithm: " + algorithm);
        }
    }

    public static String calculateCRC32(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        CRC32 crc = new CRC32();
        try (CheckedInputStream cis = new CheckedInputStream(new FileInputStream(file), crc)) {
            byte[] buffer = new byte[8192];
            while (cis.read(buffer) != -1) { /* crc updated automatically */ }
        }
        return Long.toHexString(crc.getValue());
    }

    public static byte[] readPartialFile(String relativePath, long offset, long length) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        if (offset < 0 || length < 0) throw new IOException("Invalid offset or length");
        long fileSize = file.length();
        if (offset >= fileSize) throw new IOException("Offset beyond file size");
        long actualLength = Math.min(length, fileSize - offset);
        byte[] data = new byte[(int) actualLength];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            raf.readFully(data);
        }
        return data;
    }

    public static void writePartialFile(String relativePath, long offset, byte[] data) throws IOException {
        File file = resolveSafePath(relativePath);
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(offset);
            raf.write(data);
        }
    }

    public static void appendToFile(String relativePath, String content) throws IOException {
        File file = resolveSafePath(relativePath);
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static void appendToFileBinary(String relativePath, byte[] data) throws IOException {
        File file = resolveSafePath(relativePath);
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(data);
        }
    }

    public static boolean acquireLock(String relativePath, String ownerId, long timeoutMs) throws IOException {
        String key = resolveSafePath(relativePath).getAbsolutePath();
        return fileLocks.putIfAbsent(key, new FileLock(ownerId, System.currentTimeMillis() + timeoutMs)) == null;
    }

    public static boolean releaseLock(String relativePath, String ownerId) {
        String key = getPathKey(relativePath);
        FileLock lock = fileLocks.get(key);
        if (lock != null && lock.ownerId.equals(ownerId)) {
            fileLocks.remove(key);
            return true;
        }
        return false;
    }

    public static boolean isLocked(String relativePath) {
        String key = getPathKey(relativePath);
        FileLock lock = fileLocks.get(key);
        if (lock != null && System.currentTimeMillis() > lock.expiresAt) {
            fileLocks.remove(key);
            return false;
        }
        return lock != null;
    }

    public static String getLockOwner(String relativePath) {
        String key = getPathKey(relativePath);
        FileLock lock = fileLocks.get(key);
        return lock != null ? lock.ownerId : null;
    }

    private static String getPathKey(String relativePath) {
        try {
            return resolveSafePath(relativePath).getAbsolutePath();
        } catch (IOException e) {
            return relativePath;
        }
    }

    public static void createVersion(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists() || !file.isFile()) throw new IOException("File not found");
        String key = file.getAbsolutePath();
        FileVersion versions = fileVersions.computeIfAbsent(key, k -> new FileVersion());
        String versionId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();
        versions.versions.add(0, new Version(versionId, timestamp, file.length(), readFile(relativePath)));
        while (versions.versions.size() > MAX_VERSIONS) {
            versions.versions.remove(versions.versions.size() - 1);
        }
    }

    public static List<Map<String, Object>> getVersions(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        String key = file.getAbsolutePath();
        FileVersion versions = fileVersions.get(key);
        if (versions == null) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Version v : versions.versions) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.id);
            map.put("timestamp", v.timestamp);
            map.put("size", v.size);
            result.add(map);
        }
        return result;
    }

    public static String restoreVersion(String relativePath, String versionId) throws IOException {
        File file = resolveSafePath(relativePath);
        String key = file.getAbsolutePath();
        FileVersion versions = fileVersions.get(key);
        if (versions == null) throw new IOException("No versions found");
        Version version = versions.versions.stream()
                .filter(v -> v.id.equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IOException("Version not found"));
        writeFile(relativePath, version.content);
        return versionId;
    }

    public static void setMetadata(String relativePath, String key, String value) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists()) throw new IOException("File not found");
        String absPath = file.getAbsolutePath();
        FileMetadata meta = fileMetadata.computeIfAbsent(absPath, k -> new FileMetadata());
        meta.metadata.put(key, value);
    }

    public static String getMetadata(String relativePath, String key) throws IOException {
        File file = resolveSafePath(relativePath);
        String absPath = file.getAbsolutePath();
        FileMetadata meta = fileMetadata.get(absPath);
        if (meta == null) return null;
        return meta.metadata.get(key);
    }

    public static Map<String, String> getAllMetadata(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        String absPath = file.getAbsolutePath();
        FileMetadata meta = fileMetadata.get(absPath);
        return meta != null ? new HashMap<>(meta.metadata) : Collections.emptyMap();
    }

    public static String getFileInfo(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.exists()) throw new IOException("File/Directory not found");
        StringBuilder sb = new StringBuilder();
        sb.append("Path: ").append(relativePath).append("\n");
        sb.append("Type: ").append(file.isDirectory() ? "Directory" : "File").append("\n");
        sb.append("Size: ").append(file.isFile() ? formatSize(file.length()) : formatSize(getDirectorySize(relativePath))).append("\n");
        sb.append("Last Modified: ").append(new Date(file.lastModified())).append("\n");
        if (file.isFile()) {
            try {
                sb.append("SHA-256: ").append(calculateChecksum(relativePath, "SHA-256")).append("\n");
                sb.append("MD5: ").append(calculateChecksum(relativePath, "MD5")).append("\n");
            } catch (Exception e) {
                // checksums failed
            }
        }
        Map<String, String> meta = getAllMetadata(relativePath);
        if (!meta.isEmpty()) {
            sb.append("Metadata:\n");
            meta.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }
        boolean locked = isLocked(relativePath);
        sb.append("Locked: ").append(locked).append("\n");
        if (locked) sb.append("Lock Owner: ").append(getLockOwner(relativePath)).append("\n");
        return sb.toString();
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static boolean isWritable(String relativePath) {
        try {
            File file = resolveSafePath(relativePath);
            return file.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isReadable(String relativePath) {
        try {
            File file = resolveSafePath(relativePath);
            return file.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getMimeType(String relativePath) throws IOException {
        File file = resolveSafePath(relativePath);
        if (!file.isFile()) throw new IOException("Not a file");
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "txt", "log" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js" -> "application/javascript";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "mp3" -> "audio/mpeg";
            case "mp4" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    public static void invalidateCache() {
        // Placeholder for cache invalidation
        // In a real implementation, this would clear any internal caches
    }

    public static File getConfigFile(String name) throws IOException {
        File configDir = resolveSafePath("/configs");
        if (!configDir.exists()) configDir.mkdirs();
        File cfg = new File(configDir, name + ".json");
        if (!cfg.exists()) cfg.createNewFile();
        return cfg;
    }

    public static String readConfig(String name) throws IOException {
        File cfg = getConfigFile(name);
        return Files.readString(cfg.toPath(), StandardCharsets.UTF_8);
    }

    public static void writeConfig(String name, String json) throws IOException {
        File cfg = getConfigFile(name);
        Files.writeString(cfg.toPath(), json, StandardCharsets.UTF_8);
    }
}




     class FileLock {
        final String ownerId;
        final long expiresAt;

        FileLock(String ownerId, long expiresAt) {
            this.ownerId = ownerId;
            this.expiresAt = expiresAt;
        }
    }

     class FileVersion {
        final List<Version> versions = new ArrayList<>();
    }

    class Version {
        final String id;
        final long timestamp;
        final long size;
        final String content;

        Version(String id, long timestamp, long size, String content) {
            this.id = id;
            this.timestamp = timestamp;
            this.size = size;
            this.content = content;
        }
    }

     class FileMetadata {
        final Map<String, String> metadata = new ConcurrentHashMap<>();
    }

