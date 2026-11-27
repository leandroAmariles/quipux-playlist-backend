package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RecommendationCacheKeyTest {

    private static final ProviderIdentity IDENTITY = new ProviderIdentity("v1", "model");
    private static final Song SONG = new Song("Title", "Artist", "Album", "1999", "rock");
    private static final String CATALOG = "catalog-1";

    @Test
    void isTheSameForTheSameSongsPromptAndModel() {
        RecommendationCacheKey key = RecommendationCacheKey.of(List.of(SONG), IDENTITY, CATALOG);

        assertThat(key).isEqualTo(RecommendationCacheKey.of(
                List.of(new Song("Title", "Artist", "Album", "1999", "rock")), IDENTITY, CATALOG));
        assertThat(key.songsFingerprint()).matches("[0-9a-f]{64}");
        assertThat(key.promptVersion()).isEqualTo("v1");
        assertThat(key.model()).isEqualTo("model");
    }

    static Stream<Song> variations() {
        return Stream.of(
                new Song("Other", "Artist", "Album", "1999", "rock"),
                new Song("Title", "Other", "Album", "1999", "rock"),
                new Song("Title", "Artist", "Other", "1999", "rock"),
                new Song("Title", "Artist", "Album", "2000", "rock"),
                new Song("Title", "Artist", "Album", "1999", "pop"),
                new Song("title", "Artist", "Album", "1999", "rock"),
                new Song("Title", "Artist", "Album", "1999", null),
                new Song("Title", "Artist", "Album", "1999", ""),
                new Song("TitleA", "rtist", "Album", "1999", "rock"));
    }

    @ParameterizedTest
    @MethodSource("variations")
    void changesWithAnyFieldOfASong(Song changed) {
        assertThat(RecommendationCacheKey.of(List.of(changed), IDENTITY, CATALOG))
                .isNotEqualTo(RecommendationCacheKey.of(List.of(SONG), IDENTITY, CATALOG));
    }

    @Test
    void tellsAMissingFieldFromAnEmptyOne() {
        assertThat(fingerprintOf(new Song(null, "Artist", null, null, null)))
                .isNotEqualTo(fingerprintOf(new Song("", "Artist", null, null, null)));
    }

    @Test
    void changesBeyondTheSongsThatAreSent() {
        List<Song> songs = new ArrayList<>(IntStream.range(0, 60)
                .mapToObj(index -> new Song("Song " + index, "Artist", null, null, null))
                .toList());
        RecommendationCacheKey before = RecommendationCacheKey.of(songs, IDENTITY, CATALOG);

        songs.set(59, SONG);

        assertThat(RecommendationCacheKey.of(songs, IDENTITY, CATALOG)).isNotEqualTo(before);
    }

    @Test
    void changesWithThePromptOrTheModel() {
        RecommendationCacheKey key = RecommendationCacheKey.of(List.of(SONG), IDENTITY, CATALOG);

        assertThat(RecommendationCacheKey.of(List.of(SONG), new ProviderIdentity("v2", "model"), CATALOG))
                .isNotEqualTo(key);
        assertThat(RecommendationCacheKey.of(List.of(SONG), new ProviderIdentity("v1", "other"), CATALOG))
                .isNotEqualTo(key);
    }

    @Test
    void changesWithTheVersionOfTheCatalog() {
        assertThat(RecommendationCacheKey.of(List.of(SONG), IDENTITY, "catalog-2"))
                .isNotEqualTo(RecommendationCacheKey.of(List.of(SONG), IDENTITY, CATALOG));
    }

    private static String fingerprintOf(Song song) {
        return RecommendationCacheKey.of(List.of(song), IDENTITY, CATALOG).songsFingerprint();
    }
}
