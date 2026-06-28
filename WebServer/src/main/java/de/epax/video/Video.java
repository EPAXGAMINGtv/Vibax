package de.epax.video;

import java.util.ArrayList;
import java.util.List;

public class Video {
    public String id;
    public String authorUsername;
    public String authorDisplayName;
    public String title;
    public String description;
    public List<String> tags;
    public String mediaPath;
    public String mediaType;
    public String thumbnailPath;
    public int likes;
    public int comments;
    public int shares;
    public int views;
    public long createdAt;

    public Video() {
        this.tags = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.mediaType = "video";
    }
}
