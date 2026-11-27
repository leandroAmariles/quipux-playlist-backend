package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.quipux.playlist.application.usecase.IndexCatalogUseCase;
import com.quipux.playlist.infrastructure.out.catalog.SongCatalogAdapter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * The real catalog over pgvector while a model fails: the embeddings leave the default list; the chat model leaves the
 * nearest songs, and its circuit breaker never stops the search. A new context for every test, so that no test starts
 * with the circuit of another.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecommendationEmbeddingsFailureApiTest extends AiApiTestSupport {

    @Container
    private static final PostgreSQLContainer DATABASE = CatalogDatabase.create();

    @Autowired
    private SongCatalogAdapter catalog;

    @DynamicPropertySource
    static void useTheRealCatalog(DynamicPropertyRegistry registry) {
        registry.add(InMemorySongCatalog.ENABLED, () -> "false");
        registry.add("CATALOG_DB_URL", DATABASE::getJdbcUrl);
        registry.add("CATALOG_DB_USER", DATABASE::getUsername);
        registry.add("CATALOG_DB_PASSWORD", DATABASE::getPassword);
        registry.add("AI_EMBEDDING_TIMEOUT", () -> "1s");
        registry.add("playlist.ai.circuit-breaker.window", () -> "2");
        registry.add("playlist.ai.circuit-breaker.minimum-calls", () -> "2");
    }

    static Stream<Arguments> embeddingFailures() {
        return Stream.of(
                failure("embeddings-500", aResponse().withStatus(500)),
                // The HTTP client would send these again on its own.
                failure("embeddings-503", aResponse().withStatus(503).withHeader(HttpHeaders.RETRY_AFTER, "0")),
                failure("embeddings-408", aResponse().withStatus(408)),
                failure("embeddings-slow", aResponse().withStatus(200).withFixedDelay(3_000)),
                failure("embeddings-other-size", vectorOfThreeNumbers()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("embeddingFailures")
    void usesTheDefaultsAfterOneRequestOfEmbeddingsAndNoneOfChat(String name, ResponseDefinitionBuilder response) {
        indexTheCatalog();
        LITELLM.stubFor(post(urlEqualTo(EmbeddingsStub.PATH)).willReturn(response));

        assertOrigin(name, "RESPALDO");

        assertThat(EmbeddingsStub.requestsOf(LITELLM)).hasSize(1);
        assertThat(chatRequests()).isEmpty();
    }

    @Test
    void doesNotCountTheFailuresOfTheSearchAgainstTheChatModel() {
        indexTheCatalog();
        LITELLM.stubFor(post(urlEqualTo(EmbeddingsStub.PATH)).willReturn(aResponse().withStatus(500)));
        for (int request = 0; request < 3; request++) {
            assertOrigin("search-fails-" + request, "RESPALDO");
        }

        EmbeddingsStub.answersOn(LITELLM);
        modelAnswers(suggestions("c1"));

        StepVerifier.create(create(playlist("search-recovers", Map.of("titulo", "search-recovers")))
                        .then(recommendationsOf("search-recovers")))
                .assertNext(answer -> assertThat(answer).containsEntry("origen", "IA"))
                .expectComplete()
                .verify(Duration.ofSeconds(8));
        assertThat(chatRequests()).hasSize(1);
    }

    @Test
    void keepsSearchingAndAnswersWithTheNearestSongsWhileTheCircuitIsOpen() {
        indexTheCatalog();
        gatewayAnswers(aResponse().withStatus(500));
        assertOrigin("circuit-first", "SIMILITUD");
        assertOrigin("circuit-second", "SIMILITUD");
        LITELLM.resetRequests();

        assertOrigin("circuit-open", "SIMILITUD");

        assertThat(EmbeddingsStub.requestsOf(LITELLM)).hasSize(1);
        assertThat(chatRequests()).isEmpty();
    }

    @Test
    void usesTheDefaultsWhileTheCatalogIsNotIndexed() {
        EmbeddingsStub.answersOn(LITELLM);
        StepVerifier.create(catalog.indexed()
                        .flatMap(entries -> entries.isEmpty()
                                ? Mono.<Void>empty()
                                : catalog.remove(List.copyOf(entries.keySet()))))
                .expectComplete()
                .verify(Duration.ofSeconds(30));
        LITELLM.resetRequests();

        assertOrigin("not-indexed", "RESPALDO");

        assertThat(chatRequests()).isEmpty();
    }

    private void indexTheCatalog() {
        EmbeddingsStub.answersOn(LITELLM);
        StepVerifier.create(new IndexCatalogUseCase(catalog, catalog, 50).index())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(60));
        LITELLM.resetRequests();
    }

    // The songs of every playlist are its own, so that nothing is shared between them.
    private void assertOrigin(String name, String origin) {
        StepVerifier.create(create(playlist(name, Map.of("titulo", name, "artista", "Me")))
                        .then(recommendationsOf(name)))
                .assertNext(answer -> {
                    assertThat(answer).containsEntry("origen", origin).containsEntry("versionPrompt", "");
                    assertThat(songsOf(answer)).hasSize(5);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(8));
    }

    private static Arguments failure(String name, ResponseDefinitionBuilder response) {
        return Arguments.of(name, response);
    }

    private static ResponseDefinitionBuilder vectorOfThreeNumbers() {
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(JsonMapper.builder().build().writeValueAsString(Map.of(
                        "object", "list",
                        "model", "recomendador-embeddings",
                        "data", List.of(Map.of("object", "embedding", "index", 0, "embedding", new float[] {1, 2, 3})),
                        "usage", Map.of("prompt_tokens", 1, "total_tokens", 1))));
    }
}
