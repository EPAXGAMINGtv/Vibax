package de.epax.user;

import de.epax.storageapi.StorageAPI;

public class UserManagerTest {
    public static void main(String[] args) {
        try {
            // Initialize StorageAPI
            StorageAPI.InitStorageAPI(false);
            
            // Add test servers (these would normally come from configuration)
            StorageAPI.addServer("TestServer1", "127.0.0.1:8000", "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4");
            StorageAPI.addServer("TestServer2", "127.0.0.1:8001", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
            
            // Wait a moment for servers to initialize
            Thread.sleep(2000);
            
            System.out.println("=== UserManager Test ===");
            
            // Test creating a user
            boolean created = UserManager.createUser("testuser", "Test User", "password123");
            System.out.println("User created: " + created);
            
            if (created) {
                // Test retrieving the user
                UserManager.User user = UserManager.getUser("testuser");
                System.out.println("Retrieved user: " + (user != null ? user.username : "null"));
                
                if (user != null) {
                    // Test adding a favorite
                    boolean favAdded = UserManager.addFavorite("testuser", "someitem");
                    System.out.println("Favorite added: " + favAdded);
                    
                    // Test adding a message
                    boolean msgAdded = UserManager.addMessage("testuser", "Hello world!");
                    System.out.println("Message added: " + msgAdded);
                    
                    // Test getting messages
                    java.util.List<String> messages = UserManager.getMessages("testuser");
                    System.out.println("Message count: " + messages.size());
                    
                    // Test following (need another user)
                    boolean created2 = UserManager.createUser("testuser2", "Test User 2", "password456");
                    System.out.println("Second user created: " + created2);
                    
                    if (created2) {
                        boolean followed = UserManager.followUser("testuser", "testuser2");
                        System.out.println("Followed user: " + followed);
                        
                        java.util.List<String> followers = UserManager.getFollowers("testuser2");
                        System.out.println("Follower count for testuser2: " + followers.size());
                        
                        java.util.List<String> following = UserManager.getFollowing("testuser");
                        System.out.println("Following count for testuser: " + following.size());
                        
                        // Test unfollowing
                        boolean unfollowed = UserManager.unfollowUser("testuser", "testuser2");
                        System.out.println("Unfollowed user: " + unfollowed);
                    }
                    
                    // Test removing favorite
                    boolean favRemoved = UserManager.removeFavorite("testuser", "someitem");
                    System.out.println("Favorite removed: " + favRemoved);
                }
            }
            
            // Test deleting user
            boolean deleted = UserManager.deleteUser("testuser");
            System.out.println("User deleted: " + deleted);
            
            boolean deleted2 = UserManager.deleteUser("testuser2");
            System.out.println("Second user deleted: " + deleted2);
            
            StorageAPI.shutdown();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}