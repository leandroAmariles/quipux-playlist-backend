package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaylistApiDeleteTest extends ApiTestSupport {

    @Test
    void deletesOnceAndThenAnswers404() {
        URI location = URI.create(baseUrl() + "/lists/delete-once");

        Flux<Integer> statuses = Flux.concat(
                create(playlist("delete-once", Map.of("titulo", "Uno"))),
                status(HttpMethod.DELETE, location),
                status(HttpMethod.GET, location),
                status(HttpMethod.DELETE, location));

        StepVerifier.create(statuses)
                .expectNext(201, 204, 404, 404)
                .verifyComplete();
    }

    @Test
    void answers204OnceAnd404ToTheRestForSimultaneousDeletes() {
        URI location = URI.create(baseUrl() + "/lists/delete-simultaneous");
        Flux<Integer> deletes = Flux.range(0, 20).flatMap(index -> status(HttpMethod.DELETE, location), 20);

        StepVerifier.create(create(playlist("delete-simultaneous", Map.of("titulo", "Uno")))
                        .thenMany(deletes)
                        .collectList())
                .assertNext(result -> {
                    assertThat(result).containsOnly(204, 404);
                    assertThat(Collections.frequency(result, 204)).isEqualTo(1);
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @MethodSource("com.quipux.playlist.SpecialNames#all")
    void deletesEveryAcceptedNameThroughItsLocation(String name) {
        // The prefix keeps these names apart from the ones created by other test classes.
        Mono<Integer> deleted = webClient.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(playlist("delete " + name))
                .retrieve()
                .toBodilessEntity()
                .flatMap(created -> status(
                        HttpMethod.DELETE, URI.create(baseUrl() + created.getHeaders().getLocation())));

        StepVerifier.create(deleted)
                .expectNext(204)
                .verifyComplete();
    }

    private Mono<Integer> status(HttpMethod method, URI uri) {
        return webClient.method(method).uri(uri)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
