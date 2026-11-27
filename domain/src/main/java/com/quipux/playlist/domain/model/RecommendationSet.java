package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import com.quipux.playlist.domain.model.enums.RecommendationOrigin;
import java.util.List;
import java.util.Objects;

public record RecommendationSet(
        String playlistName,
        RecommendationOrigin origin,
        String promptVersion,
        List<Recommendation> recommendations) {

    public RecommendationSet {
        Objects.requireNonNull(playlistName);
        Objects.requireNonNull(origin);
        promptVersion = origin == RecommendationOrigin.AI ? Objects.requireNonNull(promptVersion) : "";
        recommendations = recommendations.stream()
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .toList();
    }

    public static RecommendationSet fromAi(
            String playlistName, String promptVersion, List<Recommendation> recommendations) {
        return new RecommendationSet(playlistName, RecommendationOrigin.AI, promptVersion, recommendations);
    }

    /** The songs most similar to the playlist, given when the model has no result. */
    public static RecommendationSet similar(String playlistName, List<Recommendation> recommendations) {
        return new RecommendationSet(playlistName, RecommendationOrigin.SIMILARITY, "", recommendations);
    }

    public static RecommendationSet fallback(String playlistName, List<Recommendation> recommendations) {
        return new RecommendationSet(playlistName, RecommendationOrigin.FALLBACK, "", recommendations);
    }
}
