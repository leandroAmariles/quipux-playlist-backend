package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.Recommendation;
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
import reactor.test.StepVerifier;

class FileDefaultRecommendationsAdapterTest {

    private static final GenreCatalog ALLOWED = new GenreCatalog(GenreSource.LOCAL, List.of("rock"));

    @Test
    void readsTheEntriesInTheOrderOfTheirNumbers() {
        String content = entry("10", "Décima") + entry("02", "Segunda") + entry("01", "Primera")
                + entry("03", "Tercera") + entry("04", "Cuarta");

        StepVerifier.create(new FileDefaultRecommendationsAdapter(file(content), ALLOWED).defaults())
                .assertNext(defaults -> assertThat(defaults.recommendations())
                        .extracting(Recommendation::title)
                        .containsExactly("Primera", "Segunda", "Tercera", "Cuarta", "Décima"))
                .verifyComplete();
    }

    @Test
    void acceptsTheListShippedWithTheApplication() {
        GenreCatalog local = new LocalGenreProviderAdapter(new ClassPathResource("local-genres.properties"))
                .catalog();

        StepVerifier.create(new FileDefaultRecommendationsAdapter(
                        new ClassPathResource("default-recommendations.properties"), local).defaults())
                .assertNext(defaults -> assertThat(defaults.recommendations()).hasSizeGreaterThanOrEqualTo(5))
                .verifyComplete();
    }

    @Test
    void doesNotStartWithoutTheFile() {
        assertInvalid(new ClassPathResource("no-such-recommendations.properties"));
    }

    @Test
    void doesNotStartWithFewerThanFiveEntries() {
        assertInvalid(file(entries(4)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "recommendation.xx.titulo=Letters instead of a number",
            "recommendation.06.album=Unknown field",
            "something.else=Unknown key",
            "recommendation.06.titulo=Incomplete entry",
            "recommendation.01.genero=unknown-genre",
            "recommendation.01.motivo=",
            "recommendation.02.titulo=Title 1\nrecommendation.02.artista=Artist",
            "recommendation.01.titulo=\\u12"})
    void doesNotStartWithAMalformedRepeatedOrIncompleteFile(String extra) {
        assertInvalid(file(entries(5) + extra + "\n"));
    }

    private static void assertInvalid(Resource resource) {
        assertThatThrownBy(() -> new FileDefaultRecommendationsAdapter(resource, ALLOWED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003");
    }

    private static String entries(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(number -> entry("0" + number, "Title " + number))
                .collect(Collectors.joining());
    }

    private static String entry(String number, String title) {
        String prefix = "recommendation." + number + ".";
        return prefix + "titulo=" + title + "\n" + prefix + "artista=Artist\n"
                + prefix + "genero=rock\n" + prefix + "motivo=Reason\n";
    }

    private static Resource file(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
