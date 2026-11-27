package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * The real adapter with a database that is stuck: it accepts the connection and never answers, which is the slowest
 * way of being down. A refused connection is covered by {@link CatalogIsolationApiTest} and here.
 */
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class RecommendationCatalogDownApiTest extends AiApiTestSupport {

    private static final String PASSWORD = "fake-catalog-password-for-tests";
    // Nobody accepts: the operating system completes the connection and nothing is ever sent back.
    private static final ServerSocket SILENT = listen();

    @DynamicPropertySource
    static void pointToAStuckCatalog(DynamicPropertyRegistry registry) {
        registry.add(InMemorySongCatalog.ENABLED, () -> "false");
        registry.add("CATALOG_DB_URL", () -> "jdbc:postgresql://127.0.0.1:" + SILENT.getLocalPort() + "/catalog");
        registry.add("CATALOG_DB_PASSWORD", () -> PASSWORD);
    }

    @AfterAll
    static void close() throws IOException {
        SILENT.close();
    }

    @Test
    void usesTheDefaultsInTimeAndKeepsTheRestOfTheApiWorking(CapturedOutput output) {
        EmbeddingsStub.answersOn(LITELLM);
        StepVerifier.create(create(playlist("down-stuck", Map.of("titulo", "Private stuck song", "artista", "Me"))))
                .expectNext(201)
                .verifyComplete();

        StepVerifier.create(recommendationsOf("down-stuck"))
                .assertNext(answer -> {
                    assertThat(answer).containsEntry("origen", "RESPALDO").containsEntry("versionPrompt", "");
                    assertThat(songsOf(answer)).hasSize(5);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(6));

        StepVerifier.create(Flux.concat(status("/lists/down-stuck"), status("/lists"), status("/genres")))
                .expectNext(200, 200, 200)
                .verifyComplete();
        assertThat(chatRequests()).isEmpty();
        assertThat(EmbeddingsStub.requestsOf(LITELLM)).hasSize(1);
        assertThat(output)
                .contains("origin=RESPALDO", "The song catalog", "retrievalMs=", "candidates=0")
                .doesNotContain(PASSWORD, AI_KEY, "Private stuck song");
    }

    private reactor.core.publisher.Mono<Integer> status(String path) {
        return webClient.get().uri(path)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }

    private static ServerSocket listen() {
        try {
            return new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
