package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// The circuit breaker is kept closed here so that every case reaches the gateway; it has its own tests.
@TestPropertySource(properties = {
        "playlist.ai.circuit-breaker.window=1000",
        "playlist.ai.circuit-breaker.minimum-calls=1000"})
class RecommendationFailureApiTest extends AiApiTestSupport {

    private static final String VALID = suggestions("New song|New artist");

    static Stream<Arguments> failures() {
        return Stream.of(
                failure("error-401", aResponse().withStatus(401)),
                failure("error-429", aResponse().withStatus(429).withHeader(HttpHeaders.RETRY_AFTER, "1")),
                failure("error-500", aResponse().withStatus(500)),
                // The HTTP client would send these again on its own.
                failure("resend-503", aResponse().withStatus(503).withHeader(HttpHeaders.RETRY_AFTER, "0")),
                failure("resend-408", aResponse().withStatus(408)),
                failure("resend-307", aResponse().withStatus(307).withHeader(HttpHeaders.LOCATION, COMPLETIONS)),
                failure("resend-308", aResponse().withStatus(308).withHeader(HttpHeaders.LOCATION, COMPLETIONS)),
                failure("reset", aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
                failure("no-response", aResponse().withFault(Fault.EMPTY_RESPONSE)),
                failure("slow-start", streamOf(VALID).withFixedDelay(5_000)),
                failure("slow-stream", streamOf(VALID).withChunkedDribbleDelay(10, 8_000)),
                failure("empty", aResponse().withStatus(200)),
                failure("not-a-stream", aResponse().withStatus(200).withBody("<html>nope</html>")),
                failure("broken-chunk", aResponse().withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
                        .withBody("data: {not-json\n\n")),
                failure("not-json", streamOf("Claro, te recomiendo algo")),
                failure("other-shape", streamOf("{\"canciones\":[]}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    void fallsBackAfterExactlyOneRequest(String name, ResponseDefinitionBuilder response) {
        gatewayAnswers(response);
        String playlist = "fail-" + name;

        StepVerifier.create(create(playlist(playlist, Map.of("titulo", name, "artista", "Me")))
                        .then(recommendationsOf(playlist)))
                .assertNext(answer -> {
                    assertThat(answer).containsEntry("origen", "RESPALDO").containsEntry("versionPrompt", "");
                    assertThat(songsOf(answer)).hasSize(5);
                })
                .expectComplete()
                // The time limit of two seconds plus a margin.
                .verify(Duration.ofSeconds(5));

        assertThat(gatewayRequests()).hasSize(1);
    }

    @Test
    void doesNotHideAnUnknownPlaylist() {
        gatewayAnswers(aResponse().withStatus(500));

        StepVerifier.create(status("/lists/fail-unknown/recommendations"))
                .expectNext(404)
                .verifyComplete();

        assertThat(gatewayRequests()).isEmpty();
    }

    @Test
    void keepsTheRestOfTheApiWorkingWhileTheModelFails() {
        gatewayAnswers(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

        Flux<Integer> statuses = Flux.concat(
                create(playlist("fail-rest", Map.of("titulo", "Rest"))),
                status("/lists/fail-rest/recommendations"),
                status("/lists/fail-rest"),
                status("/lists"),
                status("/genres"));

        StepVerifier.create(statuses)
                .expectNext(201, 200, 200, 200, 200)
                .verifyComplete();
    }

    private static Arguments failure(String name, ResponseDefinitionBuilder response) {
        return Arguments.of(name, response);
    }

    private Mono<Integer> status(String path) {
        return webClient.get().uri(URI.create(baseUrl() + path))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
