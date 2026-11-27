package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.application.usecase.IndexCatalogUseCase;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.infrastructure.out.catalog.SongCatalogAdapter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * From the request to the database and back: the real catalog indexed in pgvector, with simulated models.
 */
@Testcontainers(disabledWithoutDocker = true)
class RecommendationsCatalogApiTest extends AiApiTestSupport {

    @Container
    private static final PostgreSQLContainer DATABASE = CatalogDatabase.create();

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Autowired
    private SongCatalogAdapter catalog;

    @DynamicPropertySource
    static void useTheRealCatalog(DynamicPropertyRegistry registry) {
        registry.add(InMemorySongCatalog.ENABLED, () -> "false");
        registry.add("CATALOG_DB_URL", DATABASE::getJdbcUrl);
        registry.add("CATALOG_DB_USER", DATABASE::getUsername);
        registry.add("CATALOG_DB_PASSWORD", DATABASE::getPassword);
    }

    @Test
    void recommendsSongsOfTheIndexedCatalogThatAreNotInThePlaylist() {
        EmbeddingsStub.answersOn(LITELLM);
        StepVerifier.create(new IndexCatalogUseCase(catalog, catalog, 50).index())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(60));
        CatalogSong known = catalog.catalog().songs().get(0);
        modelAnswers(suggestions("c1", "c2"));

        StepVerifier.create(create(playlist("catalog-real",
                        Map.of("titulo", known.title(), "artista", known.artist())))
                        .then(recommendationsOf("catalog-real")))
                .assertNext(answer -> {
                    assertThat(answer).containsEntry("origen", "IA").containsEntry("versionPrompt", "v2");
                    assertThat(songsOf(answer)).hasSize(2).allSatisfy(this::assertTakenFromTheCatalog);
                    assertThat(songsOf(answer))
                            .extracting(song -> song.get("idCatalogo"))
                            .doesNotContain(known.id().toString())
                            .doesNotHaveDuplicates();
                })
                .verifyComplete();

        assertThat(chatRequests()).singleElement().satisfies(request -> {
            JsonNode body = MAPPER.readTree(request.getBodyAsString());
            String content = body.path("messages").path(1).path("content").asString();
            List<String> offered = MAPPER.readTree(content.substring(
                            content.indexOf("<candidatas>") + "<candidatas>".length(),
                            content.indexOf("</candidatas>")))
                    .valueStream()
                    .map(candidate -> candidate.path("titulo").asString() + "|" + candidate.path("artista").asString())
                    .toList();
            assertThat(offered)
                    .hasSize(20)
                    .doesNotHaveDuplicates()
                    .doesNotContain(known.title() + "|" + known.artist());
        });
    }

    @Test
    void answersFromTheModelBySimilarityAndWithTheDefaultsWithoutBlocking() {
        EmbeddingsStub.answersOn(LITELLM);
        StepVerifier.create(new IndexCatalogUseCase(catalog, catalog, 50).index())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(60));
        // One-time class loading reads from jar files: the warm-up leaves it out of the measurement.
        StepVerifier.create(everyOutcome("blocking-warm-up"))
                .expectNextCount(3)
                .verifyComplete();
        BlockHound.install();

        // A blocking call in the search would leave the default list instead of the first two.
        StepVerifier.create(everyOutcome("blocking-measured"))
                .expectNext("IA", "SIMILITUD", "RESPALDO")
                .expectComplete()
                .verify(Duration.ofSeconds(30));
    }

    private Flux<Object> everyOutcome(String name) {
        return Flux.concat(
                Mono.fromRunnable(() -> {
                    EmbeddingsStub.answersOn(LITELLM);
                    modelAnswers(suggestions("c1"));
                }),
                originOf(name + "-ai"),
                Mono.fromRunnable(() -> gatewayAnswers(aResponse().withStatus(500))),
                originOf(name + "-similar"),
                Mono.fromRunnable(() -> LITELLM.stubFor(
                        post(urlEqualTo(EmbeddingsStub.PATH)).willReturn(aResponse().withStatus(500)))),
                originOf(name + "-defaults"));
    }

    private Mono<Object> originOf(String name) {
        return create(playlist(name, Map.of("titulo", name, "artista", "Me")))
                .then(recommendationsOf(name))
                .map(answer -> answer.get("origen"));
    }

    private void assertTakenFromTheCatalog(Map<String, Object> delivered) {
        assertThat(catalog.catalog().songs())
                .filteredOn(song -> song.id().toString().equals(delivered.get("idCatalogo")))
                .singleElement()
                .satisfies(song -> assertThat(delivered)
                        .containsEntry("titulo", song.title())
                        .containsEntry("artista", song.artist())
                        .containsEntry("genero", song.genre()));
    }
}
