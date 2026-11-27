package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenreCatalogTest {

    private static final String LONGEST = "g".repeat(100);

    @Test
    void keepsTheOrderAndDiscardsUnusableAndRepeatedGenres() {
        GenreCatalog catalog = new GenreCatalog(GenreSource.SPOTIFY, Arrays.asList(
                "salsa", null, "", "   ", "rock", "Rock", "salsa", "g".repeat(101), LONGEST));

        assertThat(catalog.genres()).containsExactly("salsa", "rock", LONGEST);
        assertThat(catalog.source()).isEqualTo(GenreSource.SPOTIFY);
        assertThat(catalog.isEmpty()).isFalse();
    }

    @Test
    void isEmptyWhenNoGenreIsUsable() {
        assertThat(new GenreCatalog(GenreSource.LOCAL, null).isEmpty()).isTrue();
        assertThat(new GenreCatalog(GenreSource.LOCAL, Arrays.asList(" ", null)).isEmpty()).isTrue();
    }

    @Test
    void cannotBeModified() {
        GenreCatalog catalog = new GenreCatalog(GenreSource.LOCAL, List.of("rock"));

        assertThatThrownBy(() -> catalog.genres().add("pop")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void includesNamesIgnoringTheCaseButNotTheSpaces() {
        GenreCatalog catalog = new GenreCatalog(GenreSource.LOCAL, List.of("rock", "hip-hop"));

        assertThat(catalog.includesAll(List.of())).isTrue();
        assertThat(catalog.includesAll(List.of("Rock", "HIP-HOP", "rock"))).isTrue();
        assertThat(catalog.includesAll(List.of("rock", "pop"))).isFalse();
        assertThat(catalog.includesAll(List.of(" rock "))).isFalse();
    }
}
