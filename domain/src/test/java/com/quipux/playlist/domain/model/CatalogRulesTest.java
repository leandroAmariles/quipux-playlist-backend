package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogRulesTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock", "pop"));

    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    void acceptsFrom200To500Songs(int size) {
        assertThatCode(() -> CatalogRules.requireValid(songs(size), ALLOWED)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 199, 501})
    void rejectsAnotherSize(int size) {
        assertThatThrownBy(() -> CatalogRules.requireValid(songs(size), ALLOWED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003")
                .hasMessageContaining("200");
    }

    @Test
    void rejectsAMissingList() {
        assertThatThrownBy(() -> CatalogRules.requireValid(null, ALLOWED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsFieldsAtTheirMaximumLength() {
        CatalogSong longest = CatalogSong.of(
                "t".repeat(255), "a".repeat(255), "b".repeat(255), "1999", "rock");

        assertThatCode(() -> CatalogRules.requireValid(with(longest), ALLOWED)).doesNotThrowAnyException();
    }

    static Stream<Arguments> invalidSongs() {
        return Stream.of(
                Arguments.of(CatalogSong.of(null, "Artist", "Album", "1999", "rock"), "title"),
                Arguments.of(CatalogSong.of("Title", " ", "Album", "1999", "rock"), "artist"),
                Arguments.of(CatalogSong.of("Title", "Artist", "", "1999", "rock"), "album"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", null, "rock"), "year"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", "1999", " "), "genre"),
                Arguments.of(CatalogSong.of("t".repeat(256), "Artist", "Album", "1999", "rock"), "title"),
                Arguments.of(CatalogSong.of("Title", "a".repeat(256), "Album", "1999", "rock"), "artist"),
                Arguments.of(CatalogSong.of("Title", "Artist", "b".repeat(256), "1999", "rock"), "album"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", "999", "rock"), "year"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", "19999", "rock"), "year"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", "19a9", "rock"), "year"),
                Arguments.of(CatalogSong.of("Title", "Artist", "Album", "1999", "unknown"), "genre"),
                Arguments.of(CatalogSong.of(" SONG 0 ", "artist", "Album", "1999", "rock"), "repeated"));
    }

    @ParameterizedTest
    @MethodSource("invalidSongs")
    void rejectsASongThatBreaksARuleAndSaysWhichAndWhere(CatalogSong invalid, String rule) {
        assertThatThrownBy(() -> CatalogRules.requireValid(with(invalid), ALLOWED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("song 201")
                .hasMessageContaining(rule);
    }

    private static List<CatalogSong> with(CatalogSong last) {
        List<CatalogSong> songs = new ArrayList<>(songs(200));
        songs.add(last);
        return songs;
    }

    private static List<CatalogSong> songs(int size) {
        return IntStream.range(0, size)
                .mapToObj(number -> CatalogSong.of("Song " + number, "Artist", "Album", "2000", "pop"))
                .collect(Collectors.toList());
    }
}
