package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.quipux.playlist.domain.model.enums.DemoUser;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// The circuit stays closed so that every failure reaches the gateway.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "playlist.ai.requests-per-minute=30",
        "playlist.ai.circuit-breaker.window=1000",
        "playlist.ai.circuit-breaker.minimum-calls=1000"})
class RecommendationNonBlockingTest extends AiApiTestSupport {

    private WebClient userClient;

    // One-time class loading reads from jar files: the warm-up leaves it out of the measurement.
    @BeforeAll
    void installBlockHoundAfterWarmingUp() {
        createClient();
        resetTheGateway();
        StepVerifier.create(tokenOf(DemoUser.USER.username(), userPassword))
                .assertNext(token -> userClient = clientWith(token))
                .verifyComplete();
        StepVerifier.create(everyOutcome("warm-up"))
                .expectNextCount(1)
                .thenConsumeWhile(status -> true)
                .verifyComplete();
        StepVerifier.create(requestsOfOneUser())
                .expectNextCount(1)
                .thenConsumeWhile(status -> true)
                .verifyComplete();
        installBlockHound();
    }

    @Test
    void answersFromTheModelTheCacheAndTheDefaultsWithoutBlocking() {
        StepVerifier.create(everyOutcome("non-blocking"))
                // model, saved answer, error, time limit, empty playlist, unknown playlist
                .expectNext("200 IA", "200 IA", "200 SIMILITUD", "200 SIMILITUD", "200 RESPALDO", "404")
                .verifyComplete();
    }

    @Test
    void rejectsARequestOverTheQuotaWithoutBlocking() {
        StepVerifier.create(requestsOfOneUser().last())
                .expectNext("429")
                .verifyComplete();
    }

    private Flux<String> everyOutcome(String name) {
        return Flux.concat(
                stubbing(() -> modelAnswers(suggestions("c1"))),
                created(name + "-ok").then(outcome(webClient, name + "-ok")),
                outcome(webClient, name + "-ok"),
                stubbing(() -> gatewayAnswers(aResponse().withStatus(500))),
                created(name + "-error").then(outcome(webClient, name + "-error")),
                stubbing(() -> gatewayAnswers(streamOf("{}").withFixedDelay(3_000))),
                created(name + "-slow").then(outcome(webClient, name + "-slow")),
                create(playlist(name + "-empty")).then(outcome(webClient, name + "-empty")),
                outcome(webClient, name + "-unknown"))
                .cast(String.class);
    }

    private Flux<String> requestsOfOneUser() {
        return Flux.range(0, 31).concatMap(index -> outcome(userClient, "quota-unknown"));
    }

    private Mono<Integer> created(String name) {
        return create(playlist(name, Map.of("titulo", name, "artista", "Me")));
    }

    private Mono<String> outcome(WebClient client, String name) {
        return client.get().uri(URI.create(baseUrl() + "/lists/" + name + "/recommendations"))
                .exchangeToMono(response -> response.bodyToMono(Map.class)
                        .map(body -> response.statusCode().is2xxSuccessful()
                                ? response.statusCode().value() + " " + body.get("origen")
                                : String.valueOf(response.statusCode().value())));
    }
}
