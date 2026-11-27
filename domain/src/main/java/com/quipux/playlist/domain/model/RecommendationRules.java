package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What can be recommended for the songs of a playlist.
 */
public final class RecommendationRules {

    private RecommendationRules() {
    }

    /**
     * The songs a model may choose from: the nearest ones that the current catalog holds and that are nowhere in the
     * playlist, in the order of their proximity. It may be empty.
     */
    public static List<CatalogSong> candidates(
            List<UUID> nearest, SongCatalog catalog, List<Song> songs, int maximum) {
        Set<SongIdentity> known = identitiesOf(songs);
        return nearest.stream()
                .distinct()
                .map(catalog::find)
                .flatMap(Optional::stream)
                .filter(song -> !known.contains(SongIdentity.of(song.title(), song.artist())))
                .limit(maximum)
                .toList();
    }

    /**
     * What a model chose among the candidates: unknown and repeated choices are discarded, the data come from the
     * catalog and a long reason is trimmed. It may be empty.
     */
    public static List<Recommendation> fromChoices(List<ModelChoice> choices, List<CatalogSong> candidates) {
        Map<UUID, CatalogSong> offered = candidates.stream()
                .collect(Collectors.toMap(CatalogSong::id, Function.identity(), (first, same) -> first));
        Map<UUID, ModelChoice> chosen = choices.stream()
                .filter(Objects::nonNull)
                .filter(choice -> offered.containsKey(choice.catalogId()))
                .collect(Collectors.toMap(ModelChoice::catalogId, Function.identity(), (first, same) -> first,
                        LinkedHashMap::new));
        return chosen.values().stream()
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .map(choice -> verified(offered.get(choice.catalogId()), choice.reason()))
                .toList();
    }

    /**
     * The nearest candidates, given when the model has no result: at most five, with a fixed reason.
     */
    public static List<Recommendation> similar(List<CatalogSong> candidates) {
        return candidates.stream()
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .map(song -> verified(song, RecommendationConstraints.SIMILARITY_REASON))
                .toList();
    }

    /**
     * The default recommendations that are not already in the playlist. It may be empty.
     */
    public static List<Recommendation> fallbackFor(DefaultRecommendations defaults, List<Song> songs) {
        Set<SongIdentity> known = identitiesOf(songs);
        return defaults.recommendations().stream()
                .filter(entry -> !known.contains(SongIdentity.of(entry.title(), entry.artist())))
                .limit(RecommendationConstraints.MAX_RECOMMENDATIONS)
                .toList();
    }

    private static Recommendation verified(CatalogSong song, String reason) {
        return new Recommendation(
                song.id().toString(),
                song.title(),
                song.artist(),
                song.genre(),
                TextLimits.trim(Objects.toString(reason, ""), RecommendationConstraints.REASON_MAX_LENGTH));
    }

    private static Set<SongIdentity> identitiesOf(List<Song> songs) {
        return songs.stream()
                .map(song -> SongIdentity.of(song.title(), song.artist()))
                .collect(Collectors.toSet());
    }
}
