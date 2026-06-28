package de.epax.user;

import java.util.ArrayList;
import java.util.List;

public class User {
    public String username;
    public String visibleName;
    public String passwordHash;
    public String bio;
    public String profilePicture;
    public List<String> favorites;
    public List<String> messages;
    public List<String> likes;
    public List<String> reposts;
    public List<String> followers;
    public List<String> following;
    public List<String> friends;
    public List<String> friendRequestsSent;
    public List<String> friendRequestsReceived;
    public List<String> watchedVideos;
    public long createdAt;

    public User() {
        this.favorites = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.likes = new ArrayList<>();
        this.reposts = new ArrayList<>();
        this.followers = new ArrayList<>();
        this.following = new ArrayList<>();
        this.friends = new ArrayList<>();
        this.friendRequestsSent = new ArrayList<>();
        this.friendRequestsReceived = new ArrayList<>();
        this.watchedVideos = new ArrayList<>();
        this.bio = "";
        this.profilePicture = "";
        this.createdAt = System.currentTimeMillis();
    }

    public User(String username, String visibleName, String passwordHash) {
        this();
        this.username = username;
        this.visibleName = visibleName;
        this.passwordHash = passwordHash;
    }
}
