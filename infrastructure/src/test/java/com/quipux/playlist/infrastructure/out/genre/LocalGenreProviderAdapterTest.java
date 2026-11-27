package com.quipux.playlist.infrastructure.out.genre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.enums.GenreSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import reactor.test.StepVerifier;

class LocalGenreProviderAdapterTest {

    @Test
    void returnsTheGenresInTheOrderOfTheirKeys() {
        LocalGenreProviderAdapter adapter =
                new LocalGenreProviderAdapter(new ClassPathResource("genres/ordered.properties"));

        StepVerifier.create(adapter.genres())
                .assertNext(genres -> assertThat(genres).containsExactly("zouk", "bambuco", "música llanera"))
                .verifyComplete();
    }

    @Test
    void shipsAListThatNeedsNoCleaning() {
        LocalGenreProviderAdapter adapter =
                new LocalGenreProviderAdapter(new ClassPathResource("local-genres.properties"));

        StepVerifier.create(adapter.genres())
                .assertNext(genres -> {
                    assertThat(genres).contains("rock", "pop", "salsa", "vallenato");
                    assertThat(new GenreCatalog(GenreSource.LOCAL, genres).genres()).isEqualTo(genres);
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"genres/empty.properties", "genres/missing.properties"})
    void refusesAListWithoutGenres(String file) {
        assertThatThrownBy(() -> new LocalGenreProviderAdapter(new ClassPathResource(file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENRE_ERROR_002")
                .hasMessageContaining(file);
    }
}
