package de.epax;

import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;
import de.epax.user.UserManager;
import de.epax.util.StorageHelper;
import de.epax.video.VideoManager;
import de.epax.web.WebServerHTTP;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class WebServer {

    public static void main(String[] args) throws IOException {
        int port = 8081;
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        StorageAPI.InitStorageAPI(false);
        Logger.info("Vibax WebServer starting on port " + port);

        seedDemoData();

        new WebServerHTTP(port, 50);
        Logger.info("Vibax ready at http://localhost:" + port);
    }

    private static void seedDemoData() {
        if (StorageHelper.exists("/users/index.json")) {
            Logger.info("Demo data already exists, skipping seed");
            return;
        }

        Logger.info("Seeding demo data...");

        UserManager.createUser("alice", "Alice", "demo123");
        UserManager.createUser("bob", "Bob", "demo123");
        UserManager.createUser("charlie", "Charlie", "demo123");
        UserManager.createUser("demo", "Demo User", "demo123");

        var alice = UserManager.getUser("alice");
        if (alice != null) {
            alice.bio = "Dance & Music creator";
            UserManager.updateUser(alice);
        }
        var bob = UserManager.getUser("bob");
        if (bob != null) {
            bob.bio = "Comedy king";
            UserManager.updateUser(bob);
        }

        UserManager.followUser("demo", "alice");
        UserManager.followUser("demo", "bob");
        UserManager.sendFriendRequest("demo", "alice");
        UserManager.acceptFriendRequest("alice", "demo");

        createDemoVideo("alice", "Epic Dance Move", "Check out this new dance!", "dance,viral,music", "gradient1");
        createDemoVideo("bob", "Funny Cat Moment", "You won't believe this!", "comedy,funny,viral", "gradient2");
        createDemoVideo("charlie", "Travel Vlog Paris", "Amazing city views", "travel,vlog,paris", "gradient3");
        createDemoVideo("alice", "Morning Routine", "Start your day right", "lifestyle,morning", "gradient4");
        createDemoVideo("bob", "Prank Gone Wrong", "Classic prank fail", "comedy,prank", "gradient5");

        Logger.info("Demo data seeded successfully");
    }

    private static void createDemoVideo(String author, String title, String desc, String tags, String gradient) {
        List<String> tagList = Arrays.asList(tags.split(","));
        String mediaPath = "/media/demo/" + gradient + ".svg";
        StorageHelper.write(mediaPath, generateGradientSvg(gradient));
        VideoManager.createVideo(author, title, desc, tagList, mediaPath, "image");
    }

    private static String generateGradientSvg(String name) {
        String[] colors = switch (name) {
            case "gradient1" -> new String[]{"#ff0050", "#00f2ea"};
            case "gradient2" -> new String[]{"#833ab4", "#fd1d1d"};
            case "gradient3" -> new String[]{"#405de6", "#5851db"};
            case "gradient4" -> new String[]{"#f09433", "#e6683c"};
            default -> new String[]{"#667eea", "#764ba2"};
        };
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"700\">" +
                "<defs><linearGradient id=\"g\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">" +
                "<stop offset=\"0%\" style=\"stop-color:" + colors[0] + "\"/>" +
                "<stop offset=\"100%\" style=\"stop-color:" + colors[1] + "\"/></linearGradient></defs>" +
                "<rect width=\"400\" height=\"700\" fill=\"url(#g)\"/></svg>";
        return "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(svg.getBytes());
    }
}
