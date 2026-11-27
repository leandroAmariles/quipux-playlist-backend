package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The fixed recommendations used when there is no answer from the model.
 */
public record DefaultRecommendations(List<Recommendation> recommendations, GenreCatalog allowedGenres) {

    public DefaultRecommendations {
        requireEnough(recommendations);
        recommendations.forEach(DefaultRecommendations::requireComplete);
        requireKnownGenres(recommendations, allowedGenres);
        requireUnique(recommendations);
        recommendations = List.copyOf(recommendations);
    }

    private static void requireEnough(List<Recommendation> entries) {
        if (entries == null || entries.size() < RecommendationConstraints.MIN_DEFAULT_RECOMMENDATIONS) {
            throw invalid("at least " + RecommendationConstraints.MIN_DEFAULT_RECOMMENDATIONS + " are required");
        }
    }

    private static void requireComplete(Recommendation entry) {
        requireField(entry.title(), RecommendationConstraints.TITLE_MAX_LENGTH);
        requireField(entry.artist(), RecommendationConstraints.ARTIST_MAX_LENGTH);
        requireField(entry.genre(), RecommendationConstraints.GENRE_MAX_LENGTH);
        requireField(entry.reason(), RecommendationConstraints.REASON_MAX_LENGTH);
    }

    private static void requireField(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw invalid("every field must hold between 1 and " + maximum + " characters");
        }
    }

    private static void requireKnownGenres(List<Recommendation> entries, GenreCatalog allowed) {
        if (!allowed.includesAll(entries.stream().map(Recommendation::genre).toList())) {
            throw invalid("every genre must be in the local genre list");
        }
    }

    private static void requireUnique(List<Recommendation> entries) {
        Set<SongIdentity> seen = new HashSet<>();
        if (!entries.stream().allMatch(entry -> seen.add(SongIdentity.of(entry.title(), entry.artist())))) {
            throw invalid("a song is repeated");
        }
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException(ErrorMessageEnum.DEFAULT_RECOMMENDATIONS_INVALID.withCode(reason));
    }
}
