package de.epax.comment;

import de.epax.user.User;
import de.epax.user.UserManager;
import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;
import de.epax.video.Video;
import de.epax.video.VideoManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommentManager {

    private static final String COMMENTS_ROOT = "/comments";

    public static String addComment(String videoId, String authorUsername, String text) {
        User author = UserManager.getUser(authorUsername);
        Video video = VideoManager.getVideo(videoId);
        if (author == null || video == null || text == null || text.isBlank()) return null;

        Comment c = new Comment();
        c.id = UUID.randomUUID().toString();
        c.videoId = videoId;
        c.authorUsername = authorUsername;
        c.authorDisplayName = author.visibleName;
        c.text = text.trim();

        StorageHelper.mkdir(COMMENTS_ROOT + "/" + videoId);

        if (StorageHelper.write(getCommentPath(videoId, c.id), toJson(c))) {
            video.comments++;
            VideoManager.updateVideo(video);
            return c.id;
        }
        return null;
    }

    public static List<Comment> getComments(String videoId) {
        String dir = COMMENTS_ROOT + "/" + videoId;
        List<String> files = StorageHelper.listFiles(dir);
        if (files == null || files.isEmpty()) return new ArrayList<>();
        return files.stream()
                .filter(f -> f.endsWith(".json"))
                .map(f -> StorageHelper.read(dir + "/" + f))
                .filter(json -> json != null)
                .map(CommentManager::fromJson)
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .collect(Collectors.toList());
    }

    private static String getCommentPath(String videoId, String commentId) {
        return COMMENTS_ROOT + "/" + videoId + "/" + commentId + ".json";
    }

    public static Map<String, Object> toPublicMap(Comment c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.id);
        map.put("videoId", c.videoId);
        map.put("authorUsername", c.authorUsername);
        map.put("authorDisplayName", c.authorDisplayName);
        map.put("text", c.text);
        map.put("likes", c.likes);
        map.put("createdAt", c.createdAt);
        return map;
    }

    public static String toJson(Comment c) {
        return JsonUtil.object(toPublicMap(c));
    }

    public static Comment fromJson(String json) {
        Comment c = new Comment();
        c.id = JsonUtil.extractString(json, "id");
        c.videoId = JsonUtil.extractString(json, "videoId");
        c.authorUsername = JsonUtil.extractString(json, "authorUsername");
        c.authorDisplayName = JsonUtil.extractString(json, "authorDisplayName");
        c.text = JsonUtil.extractString(json, "text");
        c.likes = JsonUtil.extractInt(json, "likes", 0);
        c.createdAt = JsonUtil.extractLong(json, "createdAt", System.currentTimeMillis());
        return c;
    }
}
