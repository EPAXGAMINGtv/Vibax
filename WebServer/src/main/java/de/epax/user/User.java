package de.epax.user;

import java.util.List;
import java.util.ArrayList;

/**
 * Simple User class representing a user in the system.
 * This is just a data container - no storage logic here.
 */
public class User {
    public String username;
    public String visibleName;
    public String passwordHash; // SHA-256 hash of the password
    public List<String> favorites;
    public List<String> messages;
    public List<String> likes;
    public List<String> followers;
    public List<String> following;

    public User() {
        this.favorites = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.likes = new ArrayList<>();
        this.followers = new ArrayList<>();
        this.following = new ArrayList<>();
    }

    public User(String username, String visibleName, String passwordHash) {
        this();
        this.username = username;
        this.visibleName = visibleName;
        this.passwordHash = passwordHash;
    }
}