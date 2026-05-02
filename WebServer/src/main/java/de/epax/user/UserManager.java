package de.epax.user;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * User management system that uses StorageAPI as backend.
 * User data is stored as JSON files in the storage system under /users/<username>/data.json
 */
public class UserManager {

    private static final String USERS_ROOT = "/users";
    private static final String USER_DATA_FILE = "data.json";


    /**
     * Hashes a password using SHA-256.
     * @param password Plain text password
     * @return Base64 encoded SHA-256 hash
     */
    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            Logger.error("SHA-256 not available: " + e.getMessage());
            return null;
        }
    }

    /**
     * Constructs the storage path for a user's data file.
     * @param username Username
     * @return Path in storage system
     */
    private static String getUserPath(String username) {
        return USERS_ROOT + "/" + username + "/" + USER_DATA_FILE;
    }

    /**
     * Creates a new user.
     * @param username Username (must be unique)
     * @param visibleName Display name for the user
     * @param password Plain text password (will be hashed)
     * @return true if user was created successfully, false otherwise
     */
    public static boolean createUser(String username, String visibleName, String password) {
        // Check if user already exists on any server
        if (getUser(username) != null) {
            Logger.warn("User already exists: " + username);
            return false;
        }

        // Hash the password
        String passwordHash = hashPassword(password);
        if (passwordHash == null) {
            return false;
        }

        // Create user object
        User user = new User(username, visibleName, passwordHash);

        // Convert user to JSON (simple representation)
        String json = toJson(user);

        // Store the user data - try all online servers until one succeeds
        String path = getUserPath(username);
        boolean success = false;
        
        // Get list of online servers
        for (String serverName : StorageAPI.getServerNames()) {
            // Check if server is online by getting server health
            Map<String, Object> health = StorageAPI.getServerHealth(serverName);
            if (health != null && (boolean)health.get("online")) {
                success = StorageAPI.writeFile(serverName, path, json);
                if (success) {
                    Logger.info("User created on server " + serverName + ": " + username);
                    break;
                }
            }
        }
        
        if (!success) {
            Logger.error("Failed to create user on any server: " + username);
        }
        return success;
    }

    /**
     * Retrieves a user from storage.
     * @param username Username to retrieve
     * @return User object if found, null otherwise
     */
    public static User getUser(String username) {
        String path = getUserPath(username);
        
        // Search all online servers for the user
        for (String serverName : StorageAPI.getServerNames()) {
            // Check if server is online
            Map<String, Object> health = StorageAPI.getServerHealth(serverName);
            if (health != null && (boolean)health.get("online")) {
                String json = StorageAPI.readFile(serverName, path);
                if (json != null) {
                    return fromJson(json);
                }
            }
        }
        
        return null;
    }

    /**
     * Updates an existing user's data.
     * @param user User object with updated data
     * @return true if update was successful, false otherwise
     */
    public static boolean updateUser(User user) {
        if (user == null || user.username == null) {
            return false;
        }

        String json = toJson(user);
        String path = getUserPath(user.username);
        boolean success = false;
        
        // Update on all online servers where the user exists
        for (String serverName : StorageAPI.getServerNames()) {
            // Check if server is online
            Map<String, Object> health = StorageAPI.getServerHealth(serverName);
            if (health != null && (boolean)health.get("online")) {
                // Check if user exists on this server
                String existingJson = StorageAPI.readFile(serverName, path);
                if (existingJson != null) {
                    boolean updateSuccess = StorageAPI.writeFile(serverName, path, json);
                    if (updateSuccess) {
                        success = true; // At least one update succeeded
                        Logger.info("User updated on server " + serverName + ": " + user.username);
                    }
                }
            }
        }
        
        if (!success) {
            Logger.error("Failed to update user on any server: " + (user != null ? user.username : "null"));
        }
        return success;
    }

    /**
     * Deletes a user and all their data.
     * @param username Username to delete
     * @return true if deletion was successful, false otherwise
     */
    public static boolean deleteUser(String username) {
        String path = getUserPath(username);
        boolean success = false;
        
        // Delete from all online servers
        for (String serverName : StorageAPI.getServerNames()) {
            // Check if server is online
            Map<String, Object> health = StorageAPI.getServerHealth(serverName);
            if (health != null && (boolean)health.get("online")) {
                boolean deleteSuccess = StorageAPI.deleteFile(serverName, path);
                if (deleteSuccess) {
                    success = true;
                    Logger.info("User deleted from server " + serverName + ": " + username);
                }
            }
        }
        
        if (!success) {
            Logger.error("Failed to delete user from any server: " + username);
        }
        return success;
    }

    /**
     * Adds a favorite item to a user's favorites list.
     * @param username Username
     * @param item Item to add to favorites
     * @return true if successful, false otherwise
     */
    public static boolean addFavorite(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        if (!user.favorites.contains(item)) {
            user.favorites.add(item);
            return updateUser(user);
        }
        return true; // Already exists, consider it successful
    }

    /**
     * Removes a favorite item from a user's favorites list.
     * @param username Username
     * @param item Item to remove from favorites
     * @return true if successful, false otherwise
     */
    public static boolean removeFavorite(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        boolean removed = user.favorites.remove(item);
        if (removed) {
            return updateUser(user);
        }
        return true; // Didn't exist, consider it successful
    }

    /**
     * Adds a message to a user's message list.
     * @param username Username
     * @param message Message to add
     * @return true if successful, false otherwise
     */
    public static boolean addMessage(String username, String message) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        user.messages.add(message);
        return updateUser(user);
    }

    /**
     * Retrieves a user's messages.
     * @param username Username
     * @return List of messages, or empty list if user not found
     */
    public static List<String> getMessages(String username) {
        User user = getUser(username);
        if (user == null) {
            return new ArrayList<>();
        }
        return user.messages;
    }

    /**
     * Adds a like to a user's likes list.
     * @param username Username
     * @param item Item to like
     * @return true if successful, false otherwise
     */
    public static boolean addLike(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        if (!user.likes.contains(item)) {
            user.likes.add(item);
            return updateUser(user);
        }
        return true;
    }

    /**
     * Removes a like from a user's likes list.
     * @param username Username
     * @param item Item to unlike
     * @return true if successful, false otherwise
     */
    public static boolean removeLike(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        boolean removed = user.likes.remove(item);
        if (removed) {
            return updateUser(user);
        }
        return true;
    }

    /**
     * Allows a user to follow another user.
     * @param followerUsername Username of the follower
     * @param followeeUsername Username of the user to follow
     * @return true if successful, false otherwise
     */
    public static boolean followUser(String followerUsername, String followeeUsername) {
        // Validate both users exist
        User follower = getUser(followerUsername);
        User followee = getUser(followeeUsername);
        if (follower == null || followee == null) {
            return false;
        }
        // Prevent self-following
        if (followerUsername.equals(followeeUsername)) {
            return false;
        }
        // Add to follower's following list
        if (!follower.following.contains(followeeUsername)) {
            follower.following.add(followeeUsername);
            if (!updateUser(follower)) {
                return false;
            }
        }
        // Add to followee's followers list
        if (!followee.followers.contains(followerUsername)) {
            followee.followers.add(followerUsername);
            return updateUser(followee);
        }
        return true;
    }

    /**
     * Allows a user to unfollow another user.
     * @param followerUsername Username of the follower
     * @param followeeUsername Username of the user to unfollow
     * @return true if successful, false otherwise
     */
    public static boolean unfollowUser(String followerUsername, String followeeUsername) {
        User follower = getUser(followerUsername);
        User followee = getUser(followeeUsername);
        if (follower == null || followee == null) {
            return false;
        }
        boolean followerRemoved = follower.following.remove(followeeUsername);
        boolean followeeRemoved = followee.followers.remove(followerUsername);
        
        boolean success = true;
        if (followerRemoved && !updateUser(follower)) {
            success = false;
        }
        if (followeeRemoved && !updateUser(followee)) {
            success = false;
        }
        return success;
    }

    /**
     * Gets a user's following list.
     * @param username Username
     * @return List of followed usernames, or empty list if user not found
     */
    public static List<String> getFollowing(String username) {
        User user = getUser(username);
        if (user == null) {
            return new ArrayList<>();
        }
        return user.following;
    }

    /**
     * Checks if one user is following another.
     * @param followerUsername Username of the potential follower
     * @param followeeUsername Username of the potential followee
     * @return true if the first user is following the second, false otherwise
     */
    public static boolean isFollowing(String followerUsername, String followeeUsername) {
        List<String> following = getFollowing(followerUsername);
        return following.contains(followeeUsername);
    }

    /**
     * Checks if a user has marked an item as a favorite.
     * @param username Username
     * @param item Item to check
     * @return true if the user has favorited the item, false otherwise
     */
    public static boolean isFavorite(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        return user.favorites.contains(item);
    }

    /**
     * Checks if a user has liked an item.
     * @param username Username
     * @param item Item to check
     * @return true if the user has liked the item, false otherwise
     */
    public static boolean isLiked(String username, String item) {
        User user = getUser(username);
        if (user == null) {
            return false;
        }
        return user.likes.contains(item);
    }

    /**
     * Converts a User object to a JSON string.
     * This is a simple implementation for demonstration.
     * In production, consider using a JSON library like Jackson or Gson.
     * @param user User object
     * @return JSON string representation
     */
    private static String toJson(User user) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"username\":\"").append(escapeJson(user.username)).append("\",");
        json.append("\"visibleName\":\"").append(escapeJson(user.visibleName)).append("\",");
        json.append("\"passwordHash\":\"").append(escapeJson(user.passwordHash)).append("\",");
        
        json.append("\"favorites\":[");
        for (int i = 0; i < user.favorites.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(user.favorites.get(i))).append("\"");
        }
        json.append("],");
        
        json.append("\"messages\":[");
        for (int i = 0; i < user.messages.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(user.messages.get(i))).append("\"");
        }
        json.append("],");
        
        json.append("\"likes\":[");
        for (int i = 0; i < user.likes.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(user.likes.get(i))).append("\"");
        }
        json.append("],");
        
        json.append("\"followers\":[");
        for (int i = 0; i < user.followers.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(user.followers.get(i))).append("\"");
        }
        json.append("],");
        
        json.append("\"following\":[");
        for (int i = 0; i < user.following.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(user.following.get(i))).append("\"");
        }
        json.append("]");
        
        json.append("}");
        return json.toString();
    }

    /**
     * Converts a JSON string to a User object.
     * This is a simple implementation for demonstration.
     * In production, consider using a JSON library.
     * @param json JSON string
     * @return User object
     */
    private static User fromJson(String json) {
        User user = new User();
        try {
            // Simple JSON parsing - for production use a proper JSON parser
            // Extract username
            int start = json.indexOf("\"username\":\"") + "\"username\":\"".length();
            int end = json.indexOf("\"", start);
            user.username = json.substring(start, end);
            
            // Extract visibleName
            start = json.indexOf("\"visibleName\":\"") + "\"visibleName\":\"".length();
            end = json.indexOf("\"", start);
            user.visibleName = json.substring(start, end);
            
            // Extract passwordHash
            start = json.indexOf("\"passwordHash\":\"") + "\"passwordHash\":\"".length();
            end = json.indexOf("\"", start);
            user.passwordHash = json.substring(start, end);
            
            // Extract favorites
            start = json.indexOf("\"favorites\":[") + "\"favorites\":[".length();
            end = json.indexOf("]", start);
            String favoritesJson = json.substring(start, end);
            if (!favoritesJson.isEmpty()) {
                String[] favs = favoritesJson.split("\",\"");
                for (String fav : favs) {
                    String clean = fav.replaceAll("^\"|\"$", "");
                    user.favorites.add(clean);
                }
            }
            
            // Extract messages
            start = json.indexOf("\"messages\":[") + "\"messages\":[".length();
            end = json.indexOf("]", start);
            String messagesJson = json.substring(start, end);
            if (!messagesJson.isEmpty()) {
                String[] msgs = messagesJson.split("\",\"");
                for (String msg : msgs) {
                    String clean = msg.replaceAll("^\"|\"$", "");
                    user.messages.add(clean);
                }
            }
            
            // Extract likes
            start = json.indexOf("\"likes\":[") + "\"likes\":[".length();
            end = json.indexOf("]", start);
            String likesJson = json.substring(start, end);
            if (!likesJson.isEmpty()) {
                String[] likes = likesJson.split("\",\"");
                for (String like : likes) {
                    String clean = like.replaceAll("^\"|\"$", "");
                    user.likes.add(clean);
                }
            }
            
            // Extract followers
            start = json.indexOf("\"followers\":[") + "\"followers\":[".length();
            end = json.indexOf("]", start);
            String followersJson = json.substring(start, end);
            if (!followersJson.isEmpty()) {
                String[] followers = followersJson.split("\",\"");
                for (String follower : followers) {
                    String clean = follower.replaceAll("^\"|\"$", "");
                    user.followers.add(clean);
                }
            }
            
            // Extract following
            start = json.indexOf("\"following\":[") + "\"following\":[".length();
            end = json.indexOf("]", start);
            String followingJson = json.substring(start, end);
            if (!followingJson.isEmpty()) {
                String[] following = followingJson.split("\",\"");
                for (String follow : following) {
                    String clean = follow.replaceAll("^\"|\"$", "");
                    user.following.add(clean);
                }
            }
        } catch (Exception e) {
            Logger.error("Error parsing user JSON: " + e.getMessage());
            return null;
        }
        return user;
    }

    /**
     * Escapes special characters for JSON string.
     * @param input Input string
     * @return Escaped string
     */
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}