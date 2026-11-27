package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// A new context for every test, so that no test shares quota or saved answers with another.
@TestPropertySource(properties = "playlist.ai.requests-per-minute=10")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RecommendationLimitsApiTest extends AiApiTestSupport {

    @Autowired
    private InMemorySongCatalog catalog;

    @Test
    void callsTheModelOnceForAnUnchangedPlaylist() {
        modelAnswers(suggestions("c1"));

        Flux<Map<String, Object>> answers = create(playlist("limit-cache", song("Same")))
                .thenMany(Flux.range(0, 10).concatMap(index -> recommendationsOf("limit-cache")));

        StepVerifier.create(answers.collectList())
                .assertNext(all -> assertThat(all).hasSize(10).containsOnly(all.get(0))
                        .first().satisfies(first -> assertThat(first).containsEntry("origen", "IA")))
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(1);
        assertThat(catalog.searches()).isEqualTo(1);
    }

    @Test
    void sharesTheSavedAnswerBetweenEqualPlaylistsUnderEachName() {
        modelAnswers(suggestions("c1"));

        StepVerifier.create(create(playlist("limit-first", song("Twin")))
                        .then(recommendationsOf("limit-first"))
                        .then(create(playlist("limit-second", song("Twin"))))
                        .then(recommendationsOf("limit-second")))
                .assertNext(answer -> assertThat(answer)
                        .containsEntry("lista", "limit-second")
                        .containsEntry("origen", "IA"))
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(1);
    }

    @Test
    void callsTheModelAgainWhenThePlaylistIsCreatedWithOtherSongs() {
        modelAnswers(suggestions("c1"));

        StepVerifier.create(create(playlist("limit-changed", song("Before")))
                        .then(recommendationsOf("limit-changed"))
                        .then(webClient.delete().uri("/lists/limit-changed").retrieve().toBodilessEntity())
                        .then(create(playlist("limit-changed", song("After"))))
                        .then(recommendationsOf("limit-changed")))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(2);
    }

    @Test
    void doesNotSaveAnAnswerBySimilarity() {
        gatewayAnswers(aResponse().withStatus(500));
        Mono<Map<String, Object>> failed = create(playlist("limit-retry", song("Retry")))
                .then(recommendationsOf("limit-retry"));
        StepVerifier.create(failed)
                .assertNext(answer -> assertThat(answer).containsEntry("origen", "SIMILITUD"))
                .verifyComplete();

        modelAnswers(suggestions("c1"));

        StepVerifier.create(recommendationsOf("limit-retry"))
                .assertNext(answer -> assertThat(answer).containsEntry("origen", "IA"))
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(2);
    }

    @Test
    void answersTheEleventhRequestOfAUserWith429AndKeepsServingTheOthers() {
        modelAnswers(suggestions("c1"));
        String path = "/lists/limit-quota/recommendations";
        StepVerifier.create(create(playlist("limit-quota", song("Quota")))
                        .thenMany(Flux.range(0, 10).concatMap(index -> status(webClient, HttpMethod.GET, path))))
                .expectNext(200, 200, 200, 200, 200, 200, 200, 200, 200, 200)
                .verifyComplete();

        StepVerifier.create(webClient.get().uri(path).exchangeToMono(response -> {
            assertThat(response.statusCode().value()).isEqualTo(429);
            assertThat(response.headers().asHttpHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                    .satisfies(seconds -> assertThat(Integer.parseInt(seconds)).isBetween(1, 60));
            return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
        }))
                .assertNext(problem -> assertThat(problem)
                        .containsEntry("status", 429)
                        .containsEntry("instance", path)
                        .containsEntry("code", ResponseCodeEnum.RECOMMENDATION_RATE_LIMITED.code()))
                .verifyComplete();

        StepVerifier.create(tokenOf(DemoUser.USER.username(), userPassword)
                        .flatMap(token -> status(clientWith(token), HttpMethod.GET, path)))
                .expectNext(200)
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(1);
    }

    @Test
    void countsRequestsForUnknownPlaylistsAndHeadRequests() {
        Flux<Integer> statuses = Flux.concat(
                Flux.range(0, 5).concatMap(index ->
                        status(webClient, HttpMethod.GET, "/lists/limit-unknown/recommendations")),
                Flux.range(0, 5).concatMap(index ->
                        status(webClient, HttpMethod.HEAD, "/lists/limit-unknown/recommendations")),
                status(webClient, HttpMethod.GET, "/lists/limit-unknown/recommendations"),
                // Other operations have no quota.
                status(webClient, HttpMethod.GET, "/lists"));

        StepVerifier.create(statuses)
                .expectNext(404, 404, 404, 404, 404, 404, 404, 404, 404, 404, 429, 200)
                .verifyComplete();
    }

    @Test
    void doesNotQueueTheCallsOverTheMaximum() {
        gatewayAnswers(streamOf(suggestions("c1")).withFixedDelay(1_000));
        Flux<String> names = Flux.just("limit-a", "limit-b", "limit-c");

        Mono<List<Object>> origins = names
                .concatMap(name -> create(playlist(name, song(name))))
                .thenMany(names.flatMap(this::recommendationsOf))
                .map(answer -> answer.get("origen"))
                .collectList();

        StepVerifier.create(origins)
                .assertNext(all -> assertThat(all).containsExactlyInAnyOrder("IA", "IA", "SIMILITUD"))
                .verifyComplete();
        assertThat(gatewayRequests()).hasSize(2);
    }

    @Test
    void sendsAtMostFiftySongs() {
        modelAnswers(suggestions("c1"));
        Object[] songs = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> song("Number " + index))
                .toArray();

        StepVerifier.create(create(playlist("limit-long", songs)).then(recommendationsOf("limit-long")))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(gatewayRequests()).singleElement().satisfies(request -> assertThat(request.getBodyAsString())
                .contains("Number 50")
                .doesNotContain("Number 51"));
    }

    private static Map<String, Object> song(String title) {
        return Map.of("titulo", title, "artista", "Me");
    }

    private Mono<Integer> status(WebClient client, HttpMethod method, String path) {
        return client.method(method).uri(URI.create(baseUrl() + path))
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
