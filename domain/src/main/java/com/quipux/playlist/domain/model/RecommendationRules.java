package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What can be recommended for the songs of a playlist.
 */
public final class RecommendationRules {

    private RecommendationRules() {
    }

    /**
     * The suggestions of a model that can be delivered: complete, within size, not already in the playlist and without
     * repetitions. A long reason is trimmed instead of discarding the suggestion. It may be empty.
     */
    public static List<Recommendation> validate(List<Recommendation> suggestions, List<Song> songs) {
        Set<SongIdentity> taken = new HashSet<>(identitiesOf(songs));
        return suggestions.stream()
                .filter(Objects::nonNull)
                .filter(RecommendationRules::isDeliverable)
                .filter(suggestion -> taken.add(identityOf(suggestion)))
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .map(RecommendationRules::normalized)
                .toList();
    }

    /**
     * The default recommendations that are not already in the playlist. It may be empty.
     */
    public static List<Recommendation> fallbackFor(DefaultRecommendations defaults, List<Song> songs) {
        Set<SongIdentity> known = identitiesOf(songs);
        return defaults.recommendations().stream()
                .filter(entry -> !known.contains(identityOf(entry)))
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .toList();
    }

    private static boolean isDeliverable(Recommendation suggestion) {
        return isPresent(suggestion.title(), RecommendationConstraints.TITLE_MAX_LENGTH)
                && isPresent(suggestion.artist(), RecommendationConstraints.ARTIST_MAX_LENGTH)
                && fits(suggestion.genre(), RecommendationConstraints.GENRE_MAX_LENGTH);
    }

    private static boolean isPresent(String value, int maximum) {
        return value != null && !value.isBlank() && fits(value, maximum);
    }

    private static boolean fits(String value, int maximum) {
        return value == null || value.length() <= maximum;
    }

    private static Recommendation normalized(Recommendation suggestion) {
        return new Recommendation(
                suggestion.title(),
                suggestion.artist(),
                Objects.toString(suggestion.genre(), ""),
                TextLimits.trim(Objects.toString(suggestion.reason(), ""),
                        RecommendationConstraints.REASON_MAX_LENGTH));
    }

    private static Set<SongIdentity> identitiesOf(List<Song> songs) {
        return songs.stream()
                .map(song -> SongIdentity.of(song.title(), song.artist()))
                .collect(Collectors.toSet());
    }

    private static SongIdentity identityOf(Recommendation recommendation) {
        return SongIdentity.of(recommendation.title(), recommendation.artist());
    }
}
