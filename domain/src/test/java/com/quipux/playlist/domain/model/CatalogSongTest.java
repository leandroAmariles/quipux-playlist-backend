package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CatalogSongTest {

    private static final CatalogSong SONG =
            CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time Out", "1959", "jazz");

    @Test
    void derivesTheSameIdentifierFromTheSameContent() {
        CatalogSong again = CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time Out", "1959", "jazz");

        assertThat(again.id()).isEqualTo(SONG.id());
    }

    // A value computed outside the application (MD5 of the length-prefixed fields): it must never change.
    @Test
    void keepsTheIdentifierStableOverTime() {
        assertThat(CatalogSong.of("t", "a", "b", "2000", "rock").id())
                .isEqualTo(UUID.fromString("0bbddf43-a2ce-3075-a01f-179ea6ef0077"));
    }

    static Stream<CatalogSong> differentContent() {
        return Stream.of(
                CatalogSong.of("Take Five!", "The Dave Brubeck Quartet", "Time Out", "1959", "jazz"),
                CatalogSong.of("Take Five", "Dave Brubeck", "Time Out", "1959", "jazz"),
                CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time In", "1959", "jazz"),
                CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time Out", "1960", "jazz"),
                CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time Out", "1959", "soul"));
    }

    @ParameterizedTest
    @MethodSource("differentContent")
    void changesTheIdentifierWhenAnyFieldChanges(CatalogSong other) {
        assertThat(other.id()).isNotEqualTo(SONG.id());
    }

    @Test
    void doesNotConfuseTheSameTextSplitInAnotherWay() {
        assertThat(CatalogSong.of("ab", "c", "d", "2000", "rock").id())
                .isNotEqualTo(CatalogSong.of("a", "bc", "d", "2000", "rock").id());
    }

    @Test
    void isASongOfAPlaylist() {
        assertThat(SONG.toSong())
                .isEqualTo(new Song("Take Five", "The Dave Brubeck Quartet", "Time Out", "1959", "jazz"));
    }
}
