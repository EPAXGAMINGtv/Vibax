package de.epax.storageapi;

import de.epax.storageapi.ServerConfig;

public class TestStorageFix {
    public static void main(String[] args) {
        try {
            // Initialize StorageAPI
            StorageAPI.InitStorageAPI(false);
            
            // Add test servers from config
            StorageAPI.addServer("LocalServer", "127.0.0.1:8000", "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4");
            StorageAPI.addServer("RemoteServer", "minecraft.techsvc.de:10111", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
            
            System.out.println("=== StorageAPI Fix Test ===");
            
            // Test server count
            System.out.println("Server count: " + StorageAPI.getServerCount());
            System.out.println("Online server count: " + StorageAPI.getOnlineServerCount());
            
            // Test getServerWithMostFreeSpace (should not return null anymore)
            String bestServer = StorageAPI.getServerWithMostFreeSpace();
            System.out.println("Server with most free space: " + bestServer);
            
            // Test free space for each server
            for (String serverName : StorageAPI.getServerNames()) {
                long freeSpace = StorageAPI.getFreeSpace(serverName);
                System.out.println(serverName + " free space: " + freeSpace + " bytes");
                
                // Test server health
                var health = StorageAPI.getServerHealth(serverName);
                System.out.println(serverName + " health: " + health);
                
                // Test new functions
                boolean fileExists = StorageAPI.doesFileExists(serverName, "test.txt");
                boolean dirExists = StorageAPI.doesDirExists(serverName, "/");
                boolean mkdirResult = StorageAPI.mkdir(serverName, "testdir");
                
                System.out.println(serverName + " - File exists test: " + fileExists);
                System.out.println(serverName + " - Dir exists test: " + dirExists);
                System.out.println(serverName + " - Mkdir test: " + mkdirResult);
            }
            
            StorageAPI.shutdown();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}