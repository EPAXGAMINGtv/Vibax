package de.epax.message;

import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;
import de.epax.video.Video;
import de.epax.video.VideoManager;

import java.util.*;

public final class MessageManager {

    private static final String MESSAGES_ROOT = "/messages";

    private MessageManager() {}

    public static String sendMessage(String from, String to, String text) {
        return sendMessage(from, to, text, null);
    }

    public static String sendMessage(String from, String to, String text, String videoId) {
        String id = UUID.randomUUID().toString();
        Message msg = new Message(id, from, to, text, videoId, System.currentTimeMillis());
        String path = MESSAGES_ROOT + "/" + to + "/" + id + ".json";
        if (StorageHelper.write(path, toJson(msg))) return id;
        return null;
    }

    public static List<Message> getMessages(String user, String other) {
        String dir = MESSAGES_ROOT + "/" + user;
        List<String> files = StorageHelper.listFiles(dir);
        if (files == null) return List.of();

        List<Message> all = new ArrayList<>();
        for (String f : files) {
            if (!f.endsWith(".json")) continue;
            String json = StorageHelper.read(dir + "/" + f);
            if (json == null) continue;
            Message msg = fromJson(json);
            if (msg != null && (msg.from.equals(other) || msg.to.equals(other))) {
                all.add(msg);
            }
        }
        all.sort(Comparator.comparingLong(m -> m.createdAt));
        return all;
    }

    public static int getUnreadCount(String user) {
        String dir = MESSAGES_ROOT + "/" + user;
        List<String> files = StorageHelper.listFiles(dir);
        if (files == null) return 0;
        int count = 0;
        for (String f : files) {
            if (!f.endsWith(".json")) continue;
            String json = StorageHelper.read(dir + "/" + f);
            if (json == null) continue;
            Message msg = fromJson(json);
            if (msg != null && !msg.read && !msg.from.equals(user)) count++;
        }
        return count;
    }

    public static void markRead(String user, String fromUser) {
        String dir = MESSAGES_ROOT + "/" + user;
        List<String> files = StorageHelper.listFiles(dir);
        if (files == null) return;
        for (String f : files) {
            if (!f.endsWith(".json")) continue;
            String json = StorageHelper.read(dir + "/" + f);
            if (json == null) continue;
            Message msg = fromJson(json);
            if (msg != null && msg.from.equals(fromUser) && !msg.read) {
                msg.read = true;
                StorageHelper.write(dir + "/" + f, toJson(msg));
            }
        }
    }

    public static Map<String, Object> toPublicMap(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", msg.id);
        map.put("from", msg.from);
        map.put("to", msg.to);
        map.put("text", msg.text);
        map.put("videoId", msg.videoId);
        map.put("createdAt", msg.createdAt);
        return map;
    }

    private static String toJson(Message msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", msg.id);
        map.put("from", msg.from);
        map.put("to", msg.to);
        map.put("text", msg.text);
        map.put("videoId", msg.videoId);
        map.put("createdAt", msg.createdAt);
        map.put("read", msg.read);
        return JsonUtil.object(map);
    }

    private static Message fromJson(String json) {
        try {
            String id = JsonUtil.extractString(json, "id");
            String from = JsonUtil.extractString(json, "from");
            String to = JsonUtil.extractString(json, "to");
            String text = JsonUtil.extractString(json, "text");
            String videoId = JsonUtil.extractString(json, "videoId");
            long createdAt = JsonUtil.extractLong(json, "createdAt", System.currentTimeMillis());
            if (id == null || from == null || to == null) return null;
            Message msg = new Message(id, from, to, text != null ? text : "", videoId, createdAt);
            String readStr = JsonUtil.extractString(json, "read");
            msg.read = "true".equals(readStr);
            return msg;
        } catch (Exception e) {
            return null;
        }
    }

    public static class Message {
        public String id;
        public String from;
        public String to;
        public String text;
        public String videoId;
        public long createdAt;
        public boolean read;

        public Message(String id, String from, String to, String text, String videoId, long createdAt) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.text = text;
            this.videoId = videoId;
            this.createdAt = createdAt;
            this.read = false;
        }
    }
}
