package de.epax.message;

import de.epax.util.JsonUtil;
import de.epax.util.StorageHelper;
import de.epax.video.Video;
import de.epax.video.VideoManager;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

public final class MessageManager {

    private static final String MESSAGES_ROOT = "/messages";
    private static final String ENCRYPTION_SECRET = "Vibax2024SecureKey!";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private MessageManager() {}

    private static SecretKeySpec deriveKey(String user1, String user2) {
        try {
            String sorted = user1.compareTo(user2) < 0 ? user1 + ":" + user2 : user2 + ":" + user1;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((sorted + ":" + ENCRYPTION_SECRET).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    private static String encrypt(String plaintext, String user1, String user2) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKeySpec key = deriveKey(user1, user2);
            if (key == null) return plaintext;
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            return plaintext;
        }
    }

    private static String decrypt(String encrypted, String user1, String user2) {
        if (encrypted == null || encrypted.isEmpty()) return encrypted;
        try {
            SecretKeySpec key = deriveKey(user1, user2);
            if (key == null) return encrypted;
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encrypted;
        }
    }

    public static String sendMessage(String from, String to, String text) {
        return sendMessage(from, to, text, null);
    }

    public static String sendMessage(String from, String to, String text, String videoId) {
        String id = UUID.randomUUID().toString();
        String encryptedText = encrypt(text, from, to);
        Message msg = new Message(id, from, to, encryptedText, videoId, System.currentTimeMillis());
        String receiverPath = MESSAGES_ROOT + "/" + to + "/" + id + ".json";
        String senderPath = MESSAGES_ROOT + "/" + from + "/" + id + ".json";
        if (StorageHelper.write(receiverPath, toJson(msg)) && StorageHelper.write(senderPath, toJson(msg))) return id;
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
                msg.text = decrypt(msg.text, msg.from, msg.to);
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
