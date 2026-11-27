package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultRecommendationsTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock", "pop"));

    @Test
    void acceptsFiveCompleteRecommendationsWithKnownGenres() {
        List<Recommendation> valid = valid(5);

        assertThat(new DefaultRecommendations(valid, ALLOWED).recommendations()).containsExactlyElementsOf(valid);
    }

    @Test
    void acceptsMoreThanFive() {
        assertThat(new DefaultRecommendations(valid(8), ALLOWED).recommendations()).hasSize(8);
    }

    @Test
    void rejectsFewerThanFive() {
        assertThatThrownBy(() -> new DefaultRecommendations(valid(4), ALLOWED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003");
    }

    @Test
    void rejectsAMissingList() {
        assertThatThrownBy(() -> new DefaultRecommendations(null, ALLOWED))
                .isInstanceOf(IllegalStateException.class);
    }

    static Stream<Recommendation> invalidEntries() {
        return Stream.of(
                new Recommendation(null, "Artist", "rock", "Reason"),
                new Recommendation("Title", " ", "rock", "Reason"),
                new Recommendation("Title", "Artist", "", "Reason"),
                new Recommendation("Title", "Artist", "rock", null),
                new Recommendation("t".repeat(256), "Artist", "rock", "Reason"),
                new Recommendation("Title", "a".repeat(256), "rock", "Reason"),
                new Recommendation("Title", "Artist", "g".repeat(101), "Reason"),
                new Recommendation("Title", "Artist", "rock", "r".repeat(121)),
                new Recommendation("Title", "Artist", "unknown-genre", "Reason"),
                new Recommendation(" TITLE 0 ", "artist", "rock", "Repeated"));
    }

    @ParameterizedTest
    @MethodSource("invalidEntries")
    void rejectsAnInvalidOrRepeatedEntry(Recommendation invalid) {
        List<Recommendation> entries = new ArrayList<>(valid(5));
        entries.add(invalid);

        assertThatThrownBy(() -> new DefaultRecommendations(entries, ALLOWED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003");
    }

    private static List<Recommendation> valid(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Recommendation("Title " + index, "Artist", "rock", "Reason"))
                .collect(Collectors.toList());
    }
}
