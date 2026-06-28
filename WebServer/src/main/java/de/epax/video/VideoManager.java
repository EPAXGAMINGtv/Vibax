package de.epax.video;

import de.epax.user.User;
import de.epax.user.UserManager;
import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class VideoManager {

    private static final String VIDEOS_ROOT = "/videos";
    private static final String INDEX_PATH = VIDEOS_ROOT + "/index.json";

    public static String createVideo(String authorUsername, String title, String description,
                                     List<String> tags, String mediaPath, String mediaType) {
        User author = UserManager.getUser(authorUsername);
        if (author == null) return null;

        Video video = new Video();
        video.id = UUID.randomUUID().toString();
        video.authorUsername = authorUsername;
        video.authorDisplayName = author.visibleName;
        video.title = title;
        video.description = description != null ? description : "";
        video.tags = tags != null ? tags : new ArrayList<>();
        video.mediaPath = mediaPath != null ? mediaPath : "";
        video.mediaType = mediaType != null ? mediaType : "video";
        video.thumbnailPath = "";

        if (StorageHelper.write(getVideoPath(video.id), toJson(video))) {
            addToIndex(video.id);
            return video.id;
        }
        return null;
    }

    public static Video getVideo(String id) {
        String json = StorageHelper.read(getVideoPath(id));
        return json != null ? fromJson(json) : null;
    }

    public static boolean updateVideo(Video video) {
        if (video == null || video.id == null) return false;
        return StorageHelper.write(getVideoPath(video.id), toJson(video));
    }

    public static List<Video> getAllVideos() {
        return listVideoIds().stream()
                .map(VideoManager::getVideo)
                .filter(v -> v != null)
                .collect(Collectors.toList());
    }

    public static List<Video> getVideosByUser(String username) {
        return getAllVideos().stream()
                .filter(v -> v.authorUsername != null && v.authorUsername.equals(username))
                .collect(Collectors.toList());
    }

    public static List<Video> searchVideos(String query) {
        String q = query.toLowerCase();
        return getAllVideos().stream()
                .filter(v ->
                        (v.title != null && v.title.toLowerCase().contains(q)) ||
                        (v.description != null && v.description.toLowerCase().contains(q)) ||
                        (v.authorUsername != null && v.authorUsername.toLowerCase().contains(q)) ||
                        v.tags.stream().anyMatch(t -> t.toLowerCase().contains(q))
                )
                .collect(Collectors.toList());
    }

    public static void incrementViews(String videoId) {
        Video v = getVideo(videoId);
        if (v != null) {
            v.views++;
            updateVideo(v);
        }
    }

    public static boolean likeVideo(String username, String videoId) {
        Video v = getVideo(videoId);
        if (v == null) return false;
        if (!UserManager.isLiked(username, videoId)) {
            UserManager.addLike(username, videoId);
            v.likes++;
            return updateVideo(v);
        }
        return true;
    }

    public static boolean unlikeVideo(String username, String videoId) {
        Video v = getVideo(videoId);
        if (v == null) return false;
        if (UserManager.isLiked(username, videoId)) {
            UserManager.removeLike(username, videoId);
            v.likes = Math.max(0, v.likes - 1);
            return updateVideo(v);
        }
        return true;
    }

    public static boolean shareVideo(String videoId) {
        Video v = getVideo(videoId);
        if (v == null) return false;
        v.shares++;
        return updateVideo(v);
    }

    private static String getVideoPath(String id) {
        return VIDEOS_ROOT + "/" + id + "/data.json";
    }

    private static List<String> listVideoIds() {
        String json = StorageHelper.read(INDEX_PATH);
        if (json == null) return new ArrayList<>();
        return JsonUtil.extractStringArray(json, "videos");
    }

    private static void addToIndex(String id) {
        List<String> ids = new ArrayList<>(listVideoIds());
        if (!ids.contains(id)) {
            ids.add(id);
            StorageHelper.write(INDEX_PATH, JsonUtil.object(Map.of("videos", ids)));
        }
    }

    public static Map<String, Object> toPublicMap(Video v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.id);
        map.put("authorUsername", v.authorUsername);
        map.put("authorDisplayName", v.authorDisplayName);
        map.put("title", v.title);
        map.put("description", v.description);
        map.put("tags", v.tags);
        map.put("mediaPath", v.mediaPath);
        map.put("mediaType", v.mediaType);
        map.put("thumbnailPath", v.thumbnailPath);
        map.put("likes", v.likes);
        map.put("comments", v.comments);
        map.put("shares", v.shares);
        map.put("views", v.views);
        map.put("createdAt", v.createdAt);
        return map;
    }

    public static String toJson(Video v) {
        return JsonUtil.object(toPublicMap(v));
    }

    public static Video fromJson(String json) {
        Video v = new Video();
        v.id = JsonUtil.extractString(json, "id");
        v.authorUsername = JsonUtil.extractString(json, "authorUsername");
        v.authorDisplayName = JsonUtil.extractString(json, "authorDisplayName");
        v.title = JsonUtil.extractString(json, "title");
        v.description = JsonUtil.extractString(json, "description");
        v.tags = JsonUtil.extractStringArray(json, "tags");
        v.mediaPath = JsonUtil.extractString(json, "mediaPath");
        v.mediaType = JsonUtil.extractString(json, "mediaType");
        v.thumbnailPath = JsonUtil.extractString(json, "thumbnailPath");
        v.likes = JsonUtil.extractInt(json, "likes", 0);
        v.comments = JsonUtil.extractInt(json, "comments", 0);
        v.shares = JsonUtil.extractInt(json, "shares", 0);
        v.views = JsonUtil.extractInt(json, "views", 0);
        v.createdAt = JsonUtil.extractLong(json, "createdAt", System.currentTimeMillis());
        return v;
    }
}
