package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.infrastructure.out.genre.LocalGenreProviderAdapter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class FileSongCatalogTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock"));

    @Test
    void readsTheSongsOfTheFile() {
        SongCatalog catalog = new FileSongCatalog(file(array(entries(200))), ALLOWED, "embeddings").catalog();

        assertThat(catalog.songs()).hasSize(200);
        assertThat(catalog.songs().get(0))
                .isEqualTo(CatalogSong.of("Canción 0", "Artista", "Álbum", "2000", "rock"));
        assertThat(catalog.embeddingModel()).isEqualTo("embeddings");
    }

    @Test
    void acceptsTheCatalogShippedWithTheApplication() {
        GenreCatalog local = new LocalGenreProviderAdapter(new ClassPathResource("local-genres.properties"))
                .catalog();

        SongCatalog catalog = new FileSongCatalog(
                new ClassPathResource("catalog/songs.json"), local, "embeddings").catalog();

        assertThat(catalog.songs()).hasSizeBetween(200, 500);
    }

    @Test
    void doesNotStartWithoutTheFile() {
        Resource missing = new ClassPathResource("missing-catalog.json");

        assertThatThrownBy(() -> new FileSongCatalog(missing, ALLOWED, "embeddings"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003")
                .hasMessageContaining("missing-catalog.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "[", "[1]", "[] []", "\"songs\""})
    void doesNotStartWithAMalformedFile(String content) {
        assertThatThrownBy(() -> new FileSongCatalog(file(content), ALLOWED, "embeddings"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003");
    }

    // A number is not accepted as text: the field counts as missing.
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"artista\":\"A\",\"album\":\"B\",\"anno\":\"2000\",\"genero\":\"rock\"}",
            "{\"titulo\":\"T\",\"artista\":\"A\",\"album\":\"B\",\"anno\":2000,\"genero\":\"rock\"}",
            "{\"titulo\":\"T\",\"artista\":\"A\",\"album\":\"B\",\"anno\":\"2000\",\"genero\":\"polka\"}"})
    void doesNotStartWithASongThatBreaksARuleAndSaysWhere(String invalid) {
        String content = array(entries(200) + "," + invalid);

        assertThatThrownBy(() -> new FileSongCatalog(file(content), ALLOWED, "embeddings"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("song 201");
    }

    private static String entries(int size) {
        return IntStream.range(0, size)
                .mapToObj(number -> "{\"titulo\":\"Canción " + number + "\",\"artista\":\"Artista\","
                        + "\"album\":\"Álbum\",\"anno\":\"2000\",\"genero\":\"rock\"}")
                .collect(Collectors.joining(","));
    }

    private static String array(String entries) {
        return "[" + entries + "]";
    }

    private static Resource file(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
