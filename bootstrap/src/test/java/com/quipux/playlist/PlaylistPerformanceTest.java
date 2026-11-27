package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.infrastructure.in.rest.AuthController;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaylistPerformanceTest extends ApiTestSupport {

    private static final Duration LIMIT = Duration.ofSeconds(1);
    private static final int PLAYLISTS = 100;
    private static final int SONGS_PER_PLAYLIST = 20;
    private static final int RESPONSE_BUFFER_BYTES = 5 * 1024 * 1024;

    @Test
    void answersEveryOperationInLessThanOneSecond() {
        StepVerifier.create(Flux.range(0, PLAYLISTS).concatMap(index -> create(sample("perf-" + index))))
                .expectNextCount(PLAYLISTS)
                .verifyComplete();

        URI one = URI.create(baseUrl() + "/lists/perf-50");
        WebClient largeResponses = webClient.mutate()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(RESPONSE_BUFFER_BYTES))
                .build();
        Mono<Integer> listing = largeResponses.get().uri(URI.create(baseUrl() + "/lists"))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .thenReturn(response.statusCode().value()));

        assertFast(create(sample("perf-new")), 201);
        assertFast(listing, 200);
        assertFast(status(HttpMethod.GET, one), 200);
        assertFast(status(HttpMethod.DELETE, one), 204);
    }

    @Test
    void logsInInLessThanOneSecond() {
        assertFast(anonymousClient.post().uri(AuthController.LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", DemoUser.ADMIN.username(), "password", adminPassword))
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value())), 200);
    }

    @AfterEach
    void removeSampleData() {
        StepVerifier.create(databaseClient
                        .sql("DELETE FROM song WHERE playlist_id IN (SELECT id FROM playlist WHERE name LIKE 'perf-%')")
                        .then()
                        .then(databaseClient.sql("DELETE FROM playlist WHERE name LIKE 'perf-%'").then()))
                .verifyComplete();
    }

    private static Map<String, Object> sample(String name) {
        Object[] songs = IntStream.range(0, SONGS_PER_PLAYLIST)
                .mapToObj(index -> Map.of("titulo", "Song " + index, "artista", "Artist", "album", "Album",
                        "anno", "1999", "genero", "rock"))
                .toArray();
        return playlist(name, songs);
    }

    private static void assertFast(Mono<Integer> operation, int expectedStatus) {
        Duration elapsed = StepVerifier.create(operation)
                .expectNext(expectedStatus)
                .verifyComplete();
        assertThat(elapsed).isLessThan(LIMIT);
    }

    private Mono<Integer> status(HttpMethod method, URI uri) {
        return webClient.method(method).uri(uri)
                .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()));
    }
}
