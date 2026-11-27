package com.quipux.playlist.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * A saved answer of the model. It has no playlist name: equal playlists share it, each under its own name.
 */
public record CachedRecommendations(String promptVersion, List<Recommendation> recommendations) {

    public CachedRecommendations {
        Objects.requireNonNull(promptVersion);
        recommendations = List.copyOf(recommendations);
    }
}
