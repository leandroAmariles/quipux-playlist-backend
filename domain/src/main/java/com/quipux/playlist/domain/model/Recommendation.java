package com.quipux.playlist.domain.model;

import java.util.Objects;

/**
 * A suggested song. One taken from the catalog carries its identifier; the identifier is empty when the song was not
 * verified against it.
 */
public record Recommendation(String catalogId, String title, String artist, String genre, String reason) {

    public Recommendation {
        catalogId = Objects.requireNonNullElse(catalogId, "");
    }

    /** A recommendation that is not verified against the catalog. */
    public Recommendation(String title, String artist, String genre, String reason) {
        this("", title, artist, genre, reason);
    }
}
