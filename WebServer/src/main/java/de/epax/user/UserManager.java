package de.epax.user;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;
import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UserManager {

    private static final String USERS_ROOT = "/users";
    private static final String USER_DATA_FILE = "data.json";
    private static final String USERS_INDEX = "/users/index.json";

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean verifyPassword(User user, String password) {
        if (user == null || password == null) return false;
        return user.passwordHash != null && user.passwordHash.equals(hashPassword(password));
    }

    private static String getUserPath(String username) {
        return USERS_ROOT + "/" + username + "/" + USER_DATA_FILE;
    }

    public static boolean createUser(String username, String visibleName, String password) {
        if (getUser(username) != null) return false;
        String passwordHash = hashPassword(password);
        if (passwordHash == null) return false;

        User user = new User(username, visibleName, passwordHash);
        boolean ok = StorageHelper.write(getUserPath(username), toJson(user));
        if (ok) addToUserIndex(username);
        return ok;
    }

    public static User getUser(String username) {
        String json = StorageHelper.read(getUserPath(username));
        return json != null ? fromJson(json) : null;
    }

    public static boolean updateUser(User user) {
        if (user == null || user.username == null) return false;
        return StorageHelper.write(getUserPath(user.username), toJson(user));
    }

    public static boolean deleteUser(String username) {
        return StorageHelper.delete(getUserPath(username));
    }

    public static List<String> listUsers() {
        String json = StorageHelper.read(USERS_INDEX);
        if (json == null) return new ArrayList<>();
        return JsonUtil.extractStringArray(json, "users");
    }

    private static void addToUserIndex(String username) {
        List<String> users = new ArrayList<>(listUsers());
        if (!users.contains(username)) {
            users.add(username);
            StorageHelper.write(USERS_INDEX, JsonUtil.object(Map.of("users", users)));
        }
    }

    public static List<User> searchUsers(String query) {
        String q = query.toLowerCase();
        return listUsers().stream()
                .map(UserManager::getUser)
                .filter(u -> u != null && (
                        u.username.toLowerCase().contains(q) ||
                        u.visibleName.toLowerCase().contains(q) ||
                        (u.bio != null && u.bio.toLowerCase().contains(q))
                ))
                .collect(Collectors.toList());
    }

    public static boolean followUser(String follower, String followee) {
        User f1 = getUser(follower);
        User f2 = getUser(followee);
        if (f1 == null || f2 == null || follower.equals(followee)) return false;
        if (!f1.following.contains(followee)) f1.following.add(followee);
        if (!f2.followers.contains(follower)) f2.followers.add(follower);
        return updateUser(f1) && updateUser(f2);
    }

    public static boolean unfollowUser(String follower, String followee) {
        User f1 = getUser(follower);
        User f2 = getUser(followee);
        if (f1 == null || f2 == null) return false;
        f1.following.remove(followee);
        f2.followers.remove(follower);
        return updateUser(f1) && updateUser(f2);
    }

    public static boolean sendFriendRequest(String from, String to) {
        User sender = getUser(from);
        User receiver = getUser(to);
        if (sender == null || receiver == null || from.equals(to)) return false;
        if (sender.friends.contains(to) || receiver.friends.contains(from)) return false;
        if (!sender.friendRequestsSent.contains(to)) sender.friendRequestsSent.add(to);
        if (!receiver.friendRequestsReceived.contains(from)) receiver.friendRequestsReceived.add(from);
        return updateUser(sender) && updateUser(receiver);
    }

    public static boolean acceptFriendRequest(String user, String from) {
        User u = getUser(user);
        User sender = getUser(from);
        if (u == null || sender == null) return false;
        if (!u.friendRequestsReceived.contains(from)) return false;
        u.friendRequestsReceived.remove(from);
        sender.friendRequestsSent.remove(user);
        if (!u.friends.contains(from)) u.friends.add(from);
        if (!sender.friends.contains(user)) sender.friends.add(user);
        return updateUser(u) && updateUser(sender);
    }

    public static boolean removeFriend(String user, String friend) {
        User u = getUser(user);
        User f = getUser(friend);
        if (u == null || f == null) return false;
        u.friends.remove(friend);
        f.friends.remove(user);
        return updateUser(u) && updateUser(f);
    }

    public static boolean addLike(String username, String item) {
        User user = getUser(username);
        if (user == null) return false;
        if (!user.likes.contains(item)) {
            user.likes.add(item);
            return updateUser(user);
        }
        return true;
    }

    public static boolean removeLike(String username, String item) {
        User user = getUser(username);
        if (user == null) return false;
        user.likes.remove(item);
        return updateUser(user);
    }

    public static boolean isLiked(String username, String item) {
        User user = getUser(username);
        return user != null && user.likes.contains(item);
    }

    public static void markVideoWatched(String username, String videoId) {
        User user = getUser(username);
        if (user == null) return;
        if (!user.watchedVideos.contains(videoId)) {
            user.watchedVideos.add(videoId);
            if (user.watchedVideos.size() > 500) {
                user.watchedVideos = new ArrayList<>(user.watchedVideos.subList(user.watchedVideos.size() - 500, user.watchedVideos.size()));
            }
            updateUser(user);
        }
    }

    public static Map<String, Object> toPublicMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.username);
        map.put("visibleName", user.visibleName);
        map.put("bio", user.bio != null ? user.bio : "");
        map.put("profilePicture", user.profilePicture != null ? user.profilePicture : "");
        map.put("followers", user.followers.size());
        map.put("following", user.following.size());
        map.put("friends", user.friends.size());
        map.put("createdAt", user.createdAt);
        return map;
    }

    public static String toJson(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("username", user.username);
        map.put("visibleName", user.visibleName);
        map.put("passwordHash", user.passwordHash);
        map.put("bio", user.bio != null ? user.bio : "");
        map.put("profilePicture", user.profilePicture != null ? user.profilePicture : "");
        map.put("favorites", user.favorites);
        map.put("messages", user.messages);
        map.put("likes", user.likes);
        map.put("followers", user.followers);
        map.put("following", user.following);
        map.put("friends", user.friends);
        map.put("friendRequestsSent", user.friendRequestsSent);
        map.put("friendRequestsReceived", user.friendRequestsReceived);
        map.put("watchedVideos", user.watchedVideos);
        map.put("createdAt", user.createdAt);
        return JsonUtil.object(map);
    }

    public static User fromJson(String json) {
        User user = new User();
        try {
            user.username = JsonUtil.extractString(json, "username");
            user.visibleName = JsonUtil.extractString(json, "visibleName");
            user.passwordHash = JsonUtil.extractString(json, "passwordHash");
            user.bio = JsonUtil.extractString(json, "bio");
            user.profilePicture = JsonUtil.extractString(json, "profilePicture");
            user.favorites = JsonUtil.extractStringArray(json, "favorites");
            user.messages = JsonUtil.extractStringArray(json, "messages");
            user.likes = JsonUtil.extractStringArray(json, "likes");
            user.followers = JsonUtil.extractStringArray(json, "followers");
            user.following = JsonUtil.extractStringArray(json, "following");
            user.friends = JsonUtil.extractStringArray(json, "friends");
            user.friendRequestsSent = JsonUtil.extractStringArray(json, "friendRequestsSent");
            user.friendRequestsReceived = JsonUtil.extractStringArray(json, "friendRequestsReceived");
            user.watchedVideos = JsonUtil.extractStringArray(json, "watchedVideos");
            user.createdAt = JsonUtil.extractLong(json, "createdAt", System.currentTimeMillis());
        } catch (Exception e) {
            Logger.error("Parse user error: " + e.getMessage());
            return null;
        }
        return user;
    }
}
