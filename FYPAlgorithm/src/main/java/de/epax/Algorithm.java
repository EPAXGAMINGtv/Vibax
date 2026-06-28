package de.epax;

import de.epax.fyp.RecommendationEngine;

/**
 * Entry point for the FYP recommendation module.
 * Can be used standalone for testing or as a library via WebServer.
 */
public class Algorithm {

    public static void main(String[] args) {
        RecommendationEngine engine = new RecommendationEngine();

        long now = System.currentTimeMillis();
        RecommendationEngine.VideoCandidate v1 = new RecommendationEngine.VideoCandidate();
        v1.id = "v1";
        v1.authorUsername = "alice";
        v1.tags = java.util.List.of("dance", "viral");
        v1.createdAt = now - 3600_000;
        v1.likes = 500;
        v1.comments = 80;
        v1.shares = 30;
        v1.views = 5000;

        RecommendationEngine.VideoCandidate v2 = new RecommendationEngine.VideoCandidate();
        v2.id = "v2";
        v2.authorUsername = "bob";
        v2.tags = java.util.List.of("comedy", "funny");
        v2.createdAt = now - 7200_000;
        v2.likes = 200;
        v2.comments = 40;
        v2.shares = 10;
        v2.views = 2000;

        RecommendationEngine.UserInterestProfile profile = new RecommendationEngine.UserInterestProfile();
        profile.username = "viewer";
        profile.preferredTags = java.util.Set.of("dance", "music");
        profile.following = java.util.Set.of("alice");

        var ranked = engine.rank(
                java.util.List.of(v1, v2),
                profile,
                java.util.Set.of(),
                10
        );

        System.out.println("FYP Algorithm — ranked feed:");
        for (var sv : ranked) {
            System.out.printf("  %s → score=%.4f (eng=%.2f rec=%.2f pers=%.2f soc=%.2f trend=%.2f)%n",
                    sv.videoId, sv.totalScore, sv.engagement, sv.recency, sv.personalization, sv.social, sv.trending);
        }
    }
}
