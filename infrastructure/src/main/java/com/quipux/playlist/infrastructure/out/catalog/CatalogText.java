package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The text that is compared for a song. The catalog and the playlists are written the same way, or their embeddings
 * would not be comparable.
 */
final class CatalogText {

    private static final String FIELD_SEPARATOR = " | ";

    private CatalogText() {
    }

    static String of(CatalogSong song) {
        return of(song.toSong());
    }

    static String of(Song song) {
        return Stream.of(song.title(), song.artist(), song.album(), song.releaseYear(), song.genre())
                .map(field -> Objects.toString(field, ""))
                .collect(Collectors.joining(FIELD_SEPARATOR));
    }
}
