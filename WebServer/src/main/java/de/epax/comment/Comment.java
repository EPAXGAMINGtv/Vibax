package de.epax.comment;

public class Comment {
    public String id;
    public String videoId;
    public String authorUsername;
    public String authorDisplayName;
    public String text;
    public int likes;
    public long createdAt;

    public Comment() {
        this.createdAt = System.currentTimeMillis();
    }
}
