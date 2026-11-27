package com.quipux.playlist.domain.model;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A song of the catalog. Its identifier comes from its content, so it is the same on every machine.
 */
public record CatalogSong(UUID id, String title, String artist, String album, String releaseYear, String genre) {

    public CatalogSong {
        Objects.requireNonNull(id);
    }

    public static CatalogSong of(String title, String artist, String album, String releaseYear, String genre) {
        UUID id = UUID.nameUUIDFromBytes(
                Fingerprint.bytesOf(Stream.of(title, artist, album, releaseYear, genre)));
        return new CatalogSong(id, title, artist, album, releaseYear, genre);
    }

    /** The same song as it would be added to a playlist. */
    public Song toSong() {
        return new Song(title, artist, album, releaseYear, genre);
    }
}
