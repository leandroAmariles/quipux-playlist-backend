package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaylistApiGetTest extends ApiTestSupport {

    @ParameterizedTest
    @MethodSource("com.quipux.playlist.SpecialNames#all")
    void followsTheLocationOfEveryAcceptedName(String name) {
        Mono<Map<String, Object>> found = webClient.post().uri("/lists")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(playlist(name))
                .retrieve()
                .toBodilessEntity()
                .flatMap(created -> webClient.get()
                        // A URI object is sent as it is, so the Location is not encoded a second time.
                        .uri(URI.create(baseUrl() + created.getHeaders().getLocation()))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { }));

        StepVerifier.create(found)
                .assertNext(body -> assertThat(body).containsEntry("nombre", name))
                .verifyComplete();
    }
}
