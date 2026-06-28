package de.epax.fyp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * For-You-Page recommendation engine inspired by TikTok's ranking pipeline.
 *
 * Combines engagement signals, recency decay, personalization, social graph,
 * trending velocity, and diversity constraints into a single relevance score.
 */
public class RecommendationEngine {

    private static final double WEIGHT_ENGAGEMENT     = 0.30;
    private static final double WEIGHT_RECENCY        = 0.20;
    private static final double WEIGHT_PERSONALIZATION = 0.25;
    private static final double WEIGHT_SOCIAL         = 0.15;
    private static final double WEIGHT_TRENDING       = 0.10;

    private static final double RECENCY_HALF_LIFE_HOURS = 48.0;
    private static final int    DIVERSITY_WINDOW      = 3;

    /**
     * Rank videos for a user's For You feed.
     *
     * @param videos       All candidate videos
     * @param viewer       The viewing user's profile/interests
     * @param watchedIds   Video IDs the user has already seen (for freshness boost)
     * @param limit        Max results to return
     * @return Ranked list of video IDs with scores (highest first)
     */
    public List<ScoredVideo> rank(List<VideoCandidate> videos, UserInterestProfile viewer,
                                  Set<String> watchedIds, int limit) {
        if (videos == null || videos.isEmpty()) return List.of();

        long now = System.currentTimeMillis();
        Map<String, Double> authorRecentCount = new HashMap<>();

        List<ScoredVideo> scored = new ArrayList<>();
        for (VideoCandidate video : videos) {
            double engagement     = scoreEngagement(video);
            double recency        = scoreRecency(video.createdAt, now);
            double personalization = scorePersonalization(video, viewer);
            double social         = scoreSocial(video, viewer);
            double trending       = scoreTrending(video, now);
            double freshness      = watchedIds != null && watchedIds.contains(video.id) ? 0.3 : 1.0;

            double raw = engagement * WEIGHT_ENGAGEMENT
                    + recency * WEIGHT_RECENCY
                    + personalization * WEIGHT_PERSONALIZATION
                    + social * WEIGHT_SOCIAL
                    + trending * WEIGHT_TRENDING;

            raw *= freshness;

            scored.add(new ScoredVideo(video.id, raw, engagement, recency, personalization, social, trending));
        }

        scored.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));

        // Apply diversity: penalize consecutive same-author videos
        List<ScoredVideo> diversified = applyDiversity(scored, videos, limit);

        return diversified.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Engagement score using logarithmic scaling to prevent mega-viral domination.
     */
    private double scoreEngagement(VideoCandidate v) {
        double raw = v.likes * 2.0 + v.comments * 3.0 + v.shares * 5.0 + v.views * 0.05;
        return Math.log1p(raw) / Math.log1p(10000);
    }

    /**
     * Exponential decay based on age — fresh content gets priority.
     */
    private double scoreRecency(long createdAt, long now) {
        double hours = Math.max(0, (now - createdAt) / 3_600_000.0);
        return Math.exp(-hours / RECENCY_HALF_LIFE_HOURS);
    }

    /**
     * Tag overlap + creator affinity from past likes.
     */
    private double scorePersonalization(VideoCandidate v, UserInterestProfile viewer) {
        if (viewer == null) return 0.5;

        double tagScore = 0;
        if (v.tags != null && viewer.preferredTags != null && !viewer.preferredTags.isEmpty()) {
            int matches = 0;
            for (String tag : v.tags) {
                if (viewer.preferredTags.contains(tag.toLowerCase())) matches++;
            }
            tagScore = v.tags.isEmpty() ? 0 : (double) matches / v.tags.size();
        }

        double creatorScore = viewer.likedCreators != null && viewer.likedCreators.contains(v.authorUsername) ? 0.8 : 0;

        return Math.min(1.0, tagScore * 0.6 + creatorScore * 0.4 + 0.1);
    }

    /**
     * Boost content from followed users and friends.
     */
    private double scoreSocial(VideoCandidate v, UserInterestProfile viewer) {
        if (viewer == null) return 0.2;

        if (viewer.friends != null && viewer.friends.contains(v.authorUsername)) return 1.0;
        if (viewer.following != null && viewer.following.contains(v.authorUsername)) return 0.7;
        return 0.1;
    }

    /**
     * Trending velocity: recent engagement spike relative to total views.
     */
    private double scoreTrending(VideoCandidate v, long now) {
        double ageHours = Math.max(1, (now - v.createdAt) / 3_600_000.0);
        double velocity = (v.likes + v.comments * 2 + v.shares * 3) / ageHours;
        return Math.min(1.0, velocity / 50.0);
    }

    /**
     * Re-order to avoid showing too many videos from the same creator in a row.
     */
    private List<ScoredVideo> applyDiversity(List<ScoredVideo> scored, List<VideoCandidate> videos, int limit) {
        Map<String, String> idToAuthor = new HashMap<>();
        for (VideoCandidate v : videos) {
            idToAuthor.put(v.id, v.authorUsername);
        }

        List<ScoredVideo> result = new ArrayList<>();
        List<ScoredVideo> pool = new ArrayList<>(scored);
        Set<String> recentAuthors = new HashSet<>();

        while (!pool.isEmpty() && result.size() < limit) {
            ScoredVideo best = null;
            int bestIdx = -1;

            for (int i = 0; i < pool.size(); i++) {
                ScoredVideo candidate = pool.get(i);
                String author = idToAuthor.getOrDefault(candidate.videoId, "");
                if (recentAuthors.contains(author) && recentAuthors.size() >= DIVERSITY_WINDOW) {
                    continue;
                }
                if (best == null || candidate.totalScore > best.totalScore) {
                    best = candidate;
                    bestIdx = i;
                }
            }

            if (best == null) {
                best = pool.remove(0);
            } else {
                pool.remove(bestIdx);
                result.add(best);
                String author = idToAuthor.getOrDefault(best.videoId, "");
                if (recentAuthors.size() >= DIVERSITY_WINDOW) {
                    recentAuthors.clear();
                }
                recentAuthors.add(author);
            }
        }

        return result;
    }

    /**
     * Build interest profile from user activity data.
     */
    public UserInterestProfile buildProfile(String username, List<String> following, List<String> friends,
                                            List<String> likedVideoIds, Map<String, VideoCandidate> videoIndex) {
        UserInterestProfile profile = new UserInterestProfile();
        profile.username = username;
        profile.following = following != null ? new HashSet<>(following) : new HashSet<>();
        profile.friends = friends != null ? new HashSet<>(friends) : new HashSet<>();
        profile.preferredTags = new HashSet<>();
        profile.likedCreators = new HashSet<>();

        if (likedVideoIds != null && videoIndex != null) {
            for (String vid : likedVideoIds) {
                VideoCandidate v = videoIndex.get(vid);
                if (v == null) continue;
                profile.likedCreators.add(v.authorUsername);
                if (v.tags != null) {
                    for (String tag : v.tags) {
                        profile.preferredTags.add(tag.toLowerCase());
                    }
                }
            }
        }
        return profile;
    }

    public static class VideoCandidate {
        public String id;
        public String authorUsername;
        public List<String> tags;
        public long createdAt;
        public int likes;
        public int comments;
        public int shares;
        public int views;

        public VideoCandidate() {
            this.tags = new ArrayList<>();
        }
    }

    public static class UserInterestProfile {
        public String username;
        public Set<String> following = new HashSet<>();
        public Set<String> friends = new HashSet<>();
        public Set<String> preferredTags = new HashSet<>();
        public Set<String> likedCreators = new HashSet<>();
    }

    public static class ScoredVideo {
        public final String videoId;
        public final double totalScore;
        public final double engagement;
        public final double recency;
        public final double personalization;
        public final double social;
        public final double trending;

        public ScoredVideo(String videoId, double totalScore, double engagement, double recency,
                           double personalization, double social, double trending) {
            this.videoId = videoId;
            this.totalScore = totalScore;
            this.engagement = engagement;
            this.recency = recency;
            this.personalization = personalization;
            this.social = social;
            this.trending = trending;
        }
    }
}
