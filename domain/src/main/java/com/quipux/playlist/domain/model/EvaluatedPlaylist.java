package com.quipux.playlist.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * What an evaluation looks at: the answer for a playlist, the candidates the model could choose from and who
 * answered. Without a search there are no candidates.
 */
public record EvaluatedPlaylist(RecommendationSet answer, List<CatalogSong> candidates, ProviderIdentity identity) {

    public EvaluatedPlaylist {
        Objects.requireNonNull(answer);
        Objects.requireNonNull(identity);
        candidates = List.copyOf(candidates);
    }
}
