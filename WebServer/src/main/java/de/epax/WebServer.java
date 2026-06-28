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

        new WebServerHTTP(port, 50);
        Logger.info("Vibax ready at http://localhost:" + port);
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
