package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SongCatalogTest {

    private static final CatalogSong FIRST = CatalogSong.of("First", "One", "Album", "1990", "rock");
    private static final CatalogSong SECOND = CatalogSong.of("Second", "Two", "Album", "1991", "pop");

    @Test
    void findsASongByItsIdentifier() {
        SongCatalog catalog = new SongCatalog(List.of(FIRST, SECOND), "embeddings");

        assertThat(catalog.find(SECOND.id())).contains(SECOND);
        assertThat(catalog.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void hasAShortVersion() {
        assertThat(new SongCatalog(List.of(FIRST), "embeddings").version()).matches("[0-9a-f]{12}");
    }

    @Test
    void keepsTheVersionWhenOnlyTheOrderChanges() {
        assertThat(new SongCatalog(List.of(SECOND, FIRST), "embeddings").version())
                .isEqualTo(new SongCatalog(List.of(FIRST, SECOND), "embeddings").version());
    }

    @Test
    void changesTheVersionWithTheContent() {
        CatalogSong changed = CatalogSong.of("Second", "Two", "Album", "1992", "pop");

        assertThat(new SongCatalog(List.of(FIRST, changed), "embeddings").version())
                .isNotEqualTo(new SongCatalog(List.of(FIRST, SECOND), "embeddings").version());
    }

    @Test
    void changesTheVersionWithTheEmbeddingModel() {
        assertThat(new SongCatalog(List.of(FIRST), "other").version())
                .isNotEqualTo(new SongCatalog(List.of(FIRST), "embeddings").version());
    }

    @Test
    void countsTheSongsOfTheCatalogThatAreInAPlaylist() {
        SongCatalog catalog = new SongCatalog(List.of(FIRST, SECOND), "embeddings");
        List<Song> songs = List.of(
                new Song(" FIRST ", "one", null, null, null),
                new Song("First", "One", null, null, null),
                new Song("Second", "Another", null, null, null));

        assertThat(catalog.countOf(songs)).isEqualTo(1);
    }
}
