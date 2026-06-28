package de.epax.fyp;

import de.epax.user.User;
import de.epax.user.UserManager;
import de.epax.video.Video;
import de.epax.video.VideoManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FYPService {

    private final RecommendationEngine engine = new RecommendationEngine();

    public List<Video> getFeedForUser(String username, int limit) {
        List<Video> allVideos = VideoManager.getAllVideos();
        if (allVideos.isEmpty()) return List.of();

        User user = username != null ? UserManager.getUser(username) : null;

        Map<String, RecommendationEngine.VideoCandidate> index = new HashMap<>();
        List<RecommendationEngine.VideoCandidate> candidates = new ArrayList<>();

        for (Video v : allVideos) {
            RecommendationEngine.VideoCandidate c = toCandidate(v);
            candidates.add(c);
            index.put(v.id, c);
        }

        RecommendationEngine.UserInterestProfile profile = null;
        Set<String> watched = new HashSet<>();

        if (user != null) {
            profile = engine.buildProfile(
                    user.username,
                    user.following,
                    user.friends,
                    user.likes,
                    index
            );
            watched = new HashSet<>(user.watchedVideos);
        }

        List<RecommendationEngine.ScoredVideo> ranked = engine.rank(candidates, profile, watched, limit);

        Map<String, Video> videoMap = allVideos.stream().collect(Collectors.toMap(v -> v.id, v -> v));
        List<Video> feed = new ArrayList<>();
        for (RecommendationEngine.ScoredVideo sv : ranked) {
            Video v = videoMap.get(sv.videoId);
            if (v != null) feed.add(v);
        }
        return feed;
    }

    private RecommendationEngine.VideoCandidate toCandidate(Video v) {
        RecommendationEngine.VideoCandidate c = new RecommendationEngine.VideoCandidate();
        c.id = v.id;
        c.authorUsername = v.authorUsername;
        c.tags = v.tags;
        c.createdAt = v.createdAt;
        c.likes = v.likes;
        c.comments = v.comments;
        c.shares = v.shares;
        c.views = v.views;
        return c;
    }
}
