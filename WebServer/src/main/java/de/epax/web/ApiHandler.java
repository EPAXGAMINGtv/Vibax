package de.epax.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.epax.auth.SessionManager;
import de.epax.comment.CommentManager;
import de.epax.fyp.FYPService;
import de.epax.user.User;
import de.epax.user.UserManager;
import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;
import de.epax.video.Video;
import de.epax.video.VideoManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ApiHandler implements HttpHandler {

    private final FYPService fypService = new FYPService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String body = readBody(exchange);

        try {
            if (path.equals("/api/auth/register") && "POST".equals(method)) {
                handleRegister(body, exchange);
            } else if (path.equals("/api/auth/login") && "POST".equals(method)) {
                handleLogin(body, exchange);
            } else if (path.equals("/api/auth/logout") && "POST".equals(method)) {
                handleLogout(exchange);
            } else if (path.equals("/api/auth/me") && "GET".equals(method)) {
                handleMe(exchange);
            } else if (path.startsWith("/api/users/") && "GET".equals(method)) {
                handleGetUser(path, exchange);
            } else if (path.equals("/api/users/search") && "GET".equals(method)) {
                handleSearchUsers(exchange);
            } else if (path.equals("/api/follow") && "POST".equals(method)) {
                handleFollow(body, exchange);
            } else if (path.equals("/api/unfollow") && "POST".equals(method)) {
                handleUnfollow(body, exchange);
            } else if (path.equals("/api/friends/request") && "POST".equals(method)) {
                handleFriendRequest(body, exchange);
            } else if (path.equals("/api/friends/accept") && "POST".equals(method)) {
                handleFriendAccept(body, exchange);
            } else if (path.equals("/api/profile") && "PUT".equals(method)) {
                handleUpdateProfile(body, exchange);
            } else if (path.equals("/api/feed") && "GET".equals(method)) {
                handleFeed(exchange);
            } else if (path.equals("/api/videos") && "GET".equals(method)) {
                handleListVideos(exchange);
            } else if (path.equals("/api/videos") && "POST".equals(method)) {
                handleCreateVideo(body, exchange);
            } else if (path.startsWith("/api/videos/") && "GET".equals(method)) {
                handleGetVideo(path, exchange);
            } else if (path.equals("/api/videos/search") && "GET".equals(method)) {
                handleSearchVideos(exchange);
            } else if (path.equals("/api/like") && "POST".equals(method)) {
                handleLike(body, exchange);
            } else if (path.equals("/api/unlike") && "POST".equals(method)) {
                handleUnlike(body, exchange);
            } else if (path.equals("/api/share") && "POST".equals(method)) {
                handleShare(body, exchange);
            } else if (path.equals("/api/watch") && "POST".equals(method)) {
                handleWatch(body, exchange);
            } else if (path.startsWith("/api/comments/") && "GET".equals(method)) {
                handleGetComments(path, exchange);
            } else if (path.equals("/api/comments") && "POST".equals(method)) {
                handleAddComment(body, exchange);
            } else if (path.startsWith("/api/media/") && "GET".equals(method)) {
                handleMedia(path, exchange);
            } else if (path.equals("/api/media") && "POST".equals(method)) {
                handleUploadMedia(body, exchange);
            } else {
                sendJson(exchange, 404, JsonUtil.object(Map.of("error", "Not found")));
            }
        } catch (Exception e) {
            sendJson(exchange, 500, JsonUtil.object(Map.of("error", e.getMessage())));
        }
    }

    private void handleRegister(String body, HttpExchange ex) throws IOException {
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String username = data.get("username");
        String visibleName = data.get("visibleName");
        String password = data.get("password");
        if (username == null || password == null) {
            sendJson(ex, 400, JsonUtil.object(Map.of("error", "username and password required")));
            return;
        }
        if (UserManager.createUser(username, visibleName != null ? visibleName : username, password)) {
            String token = SessionManager.createSession(username);
            sendJson(ex, 201, JsonUtil.object(Map.of("token", token, "username", username)));
        } else {
            sendJson(ex, 409, JsonUtil.object(Map.of("error", "User already exists")));
        }
    }

    private void handleLogin(String body, HttpExchange ex) throws IOException {
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        User user = UserManager.getUser(data.get("username"));
        if (user != null && UserManager.verifyPassword(user, data.get("password"))) {
            String token = SessionManager.createSession(user.username);
            sendJson(ex, 200, JsonUtil.object(Map.of("token", token, "user", UserManager.toPublicMap(user))));
        } else {
            sendJson(ex, 401, JsonUtil.object(Map.of("error", "Invalid credentials")));
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String token = getToken(ex);
        if (token != null) SessionManager.invalidate(token);
        sendJson(ex, 200, JsonUtil.object(Map.of("ok", true)));
    }

    private void handleMe(HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, Object> map = UserManager.toPublicMap(user);
        map.put("likes", user.likes);
        map.put("friendsList", user.friends);
        map.put("friendRequestsReceived", user.friendRequestsReceived);
        sendJson(ex, 200, JsonUtil.object(map));
    }

    private void handleGetUser(String path, HttpExchange ex) throws IOException {
        String username = path.substring("/api/users/".length());
        User user = UserManager.getUser(username);
        if (user == null) {
            sendJson(ex, 404, JsonUtil.object(Map.of("error", "User not found")));
            return;
        }
        User viewer = getUser(ex);
        Map<String, Object> map = UserManager.toPublicMap(user);
        if (viewer != null) {
            map.put("isFollowing", viewer.following.contains(username));
            map.put("isFriend", viewer.friends.contains(username));
            map.put("friendRequestSent", viewer.friendRequestsSent.contains(username));
        }
        sendJson(ex, 200, JsonUtil.object(map));
    }

    private void handleSearchUsers(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        String query = q != null && q.startsWith("q=") ? decode(q.substring(2)) : "";
        List<Map<String, Object>> results = UserManager.searchUsers(query).stream()
                .map(UserManager::toPublicMap).collect(Collectors.toList());
        sendJson(ex, 200, JsonUtil.object(Map.of("users", results)));
    }

    private void handleFollow(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = UserManager.followUser(user.username, data.get("username"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleUnfollow(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = UserManager.unfollowUser(user.username, data.get("username"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleFriendRequest(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = UserManager.sendFriendRequest(user.username, data.get("username"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleFriendAccept(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = UserManager.acceptFriendRequest(user.username, data.get("username"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleUpdateProfile(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        if (data.containsKey("visibleName")) user.visibleName = data.get("visibleName");
        if (data.containsKey("bio")) user.bio = data.get("bio");
        if (data.containsKey("profilePicture")) user.profilePicture = data.get("profilePicture");
        boolean ok = UserManager.updateUser(user);
        sendJson(ex, ok ? 200 : 500, JsonUtil.object(Map.of("ok", ok, "user", UserManager.toPublicMap(user))));
    }

    private void handleFeed(HttpExchange ex) throws IOException {
        User user = getUser(ex);
        String username = user != null ? user.username : null;
        int limit = 20;
        String q = ex.getRequestURI().getQuery();
        if (q != null && q.contains("limit=")) {
            try { limit = Integer.parseInt(q.split("limit=")[1].split("&")[0]); } catch (Exception ignored) {}
        }
        List<Map<String, Object>> feed = fypService.getFeedForUser(username, limit).stream()
                .map(VideoManager::toPublicMap).collect(Collectors.toList());
        sendJson(ex, 200, JsonUtil.object(Map.of("videos", feed)));
    }

    private void handleListVideos(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        List<Video> videos;
        if (q != null && q.contains("user=")) {
            String user = decode(q.split("user=")[1].split("&")[0]);
            videos = VideoManager.getVideosByUser(user);
        } else {
            videos = VideoManager.getAllVideos();
        }
        List<Map<String, Object>> list = videos.stream().map(VideoManager::toPublicMap).collect(Collectors.toList());
        sendJson(ex, 200, JsonUtil.object(Map.of("videos", list)));
    }

    private void handleCreateVideo(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        List<String> tags = data.containsKey("tags") ?
                Arrays.asList(data.get("tags").split(",")) : new ArrayList<>();
        String id = VideoManager.createVideo(
                user.username,
                data.get("title"),
                data.get("description"),
                tags,
                data.get("mediaPath"),
                data.getOrDefault("mediaType", "video")
        );
        if (id != null) {
            sendJson(ex, 201, JsonUtil.object(Map.of("id", id)));
        } else {
            sendJson(ex, 500, JsonUtil.object(Map.of("error", "Failed to create video")));
        }
    }

    private void handleGetVideo(String path, HttpExchange ex) throws IOException {
        String id = path.substring("/api/videos/".length());
        Video video = VideoManager.getVideo(id);
        if (video == null) {
            sendJson(ex, 404, JsonUtil.object(Map.of("error", "Video not found")));
            return;
        }
        VideoManager.incrementViews(id);
        sendJson(ex, 200, JsonUtil.object(VideoManager.toPublicMap(video)));
    }

    private void handleSearchVideos(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        String query = q != null && q.startsWith("q=") ? decode(q.substring(2)) : "";
        List<Map<String, Object>> results = VideoManager.searchVideos(query).stream()
                .map(VideoManager::toPublicMap).collect(Collectors.toList());
        sendJson(ex, 200, JsonUtil.object(Map.of("videos", results)));
    }

    private void handleLike(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = VideoManager.likeVideo(user.username, data.get("videoId"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleUnlike(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = VideoManager.unlikeVideo(user.username, data.get("videoId"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleShare(String body, HttpExchange ex) throws IOException {
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        boolean ok = VideoManager.shareVideo(data.get("videoId"));
        sendJson(ex, ok ? 200 : 400, JsonUtil.object(Map.of("ok", ok)));
    }

    private void handleWatch(String body, HttpExchange ex) throws IOException {
        User user = getUser(ex);
        if (user == null) {
            sendJson(ex, 200, JsonUtil.object(Map.of("ok", true)));
            return;
        }
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        UserManager.markVideoWatched(user.username, data.get("videoId"));
        sendJson(ex, 200, JsonUtil.object(Map.of("ok", true)));
    }

    private void handleGetComments(String path, HttpExchange ex) throws IOException {
        String videoId = path.substring("/api/comments/".length());
        List<Map<String, Object>> comments = CommentManager.getComments(videoId).stream()
                .map(CommentManager::toPublicMap).collect(Collectors.toList());
        sendJson(ex, 200, JsonUtil.object(Map.of("comments", comments)));
    }

    private void handleAddComment(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String id = CommentManager.addComment(data.get("videoId"), user.username, data.get("text"));
        if (id != null) {
            sendJson(ex, 201, JsonUtil.object(Map.of("id", id)));
        } else {
            sendJson(ex, 400, JsonUtil.object(Map.of("error", "Failed to add comment")));
        }
    }

    private void handleMedia(String path, HttpExchange ex) throws IOException {
        String mediaPath = decode(path.substring("/api/media/".length()));
        if (!mediaPath.startsWith("/")) mediaPath = "/" + mediaPath;
        String content = StorageHelper.read(mediaPath);
        if (content == null) {
            sendJson(ex, 404, JsonUtil.object(Map.of("error", "Media not found")));
            return;
        }
        byte[] bytes;
        if (content.startsWith("data:")) {
            int comma = content.indexOf(',');
            bytes = java.util.Base64.getDecoder().decode(content.substring(comma + 1));
        } else {
            bytes = content.getBytes(StandardCharsets.UTF_8);
        }
        String contentType = "application/octet-stream";
        if (mediaPath.endsWith(".jpg") || mediaPath.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (mediaPath.endsWith(".png")) contentType = "image/png";
        else if (mediaPath.endsWith(".mp4")) contentType = "video/mp4";
        else if (mediaPath.endsWith(".webm")) contentType = "video/webm";
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void handleUploadMedia(String body, HttpExchange ex) throws IOException {
        User user = requireUser(ex);
        if (user == null) return;
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String base64 = data.get("data");
        String filename = data.get("filename");
        if (base64 == null || filename == null) {
            sendJson(ex, 400, JsonUtil.object(Map.of("error", "data and filename required")));
            return;
        }
        String path = "/media/" + user.username + "/" + System.currentTimeMillis() + "_" + filename;
        String stored = "data:application/octet-stream;base64," + base64;
        if (StorageHelper.write(path, stored)) {
            sendJson(ex, 201, JsonUtil.object(Map.of("path", path)));
        } else {
            sendJson(ex, 500, JsonUtil.object(Map.of("error", "Upload failed")));
        }
    }

    private User getUser(HttpExchange ex) {
        String token = getToken(ex);
        if (token == null) return null;
        String username = SessionManager.getUsername(token);
        return username != null ? UserManager.getUser(username) : null;
    }

    private User requireUser(HttpExchange ex) throws IOException {
        User user = getUser(ex);
        if (user == null) {
            sendJson(ex, 401, JsonUtil.object(Map.of("error", "Unauthorized")));
        }
        return user;
    }

    private String getToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return ex.getRequestHeaders().getFirst("X-Auth-Token");
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Auth-Token");
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCors(ex);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
