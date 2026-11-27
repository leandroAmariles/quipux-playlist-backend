package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PlaylistApiListTest extends ApiTestSupport {

    @Test
    void listsWhatWasCreatedKeepingTheOrder() {
        Mono<List<Map<String, Object>>> listing = create(playlist("list-b", Map.of("titulo", "2"),
                        Map.of("titulo", "1")))
                .then(create(playlist("list-a")))
                .then(webClient.get().uri("/lists").retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() { }));

        StepVerifier.create(listing)
                .assertNext(playlists -> {
                    List<Map<String, Object>> own = playlists.stream()
                            .filter(playlist -> String.valueOf(playlist.get("nombre")).startsWith("list-"))
                            .toList();
                    assertThat(own).extracting(playlist -> playlist.get("nombre"))
                            .containsExactly("list-b", "list-a");
                    assertThat(titlesOf(own.get(0))).containsExactly("2", "1");
                    assertThat(titlesOf(own.get(1))).isEmpty();
                })
                .verifyComplete();
    }

    private static List<Object> titlesOf(Map<String, Object> playlist) {
        return ((List<?>) playlist.get("canciones")).stream()
                .<Object>map(song -> ((Map<?, ?>) song).get("titulo"))
                .toList();
    }
}
