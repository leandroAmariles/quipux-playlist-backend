package com.quipux.playlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PlaylistApiCreateTest extends ApiTestSupport {

    @Test
    void createsAPlaylistOnceAndRejectsTheSameNameAfterwards() {
        Map<String, Object> body = playlist("create-once", Map.of("titulo", "Uno"), Map.of("titulo", "Dos"));

        StepVerifier.create(create(body).concatWith(create(body)))
                .expectNext(201, 409)
                .verifyComplete();

        StepVerifier.create(count("SELECT COUNT(*) FROM song s JOIN playlist p ON p.id = s.playlist_id "
                        + "WHERE p.name = 'create-once'"))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void answers201OnceAnd409ToTheRestForSimultaneousRequests() {
        Flux<Integer> statuses = Flux.range(0, 20)
                .flatMap(index -> create(playlist("simultaneous")), 20);

        StepVerifier.create(statuses.collectList())
                .assertNext(result -> {
                    assertThat(result).containsOnly(201, 409);
                    assertThat(Collections.frequency(result, 201)).isEqualTo(1);
                    assertThat(Collections.frequency(result, 409)).isEqualTo(19);
                })
                .verifyComplete();
    }

    @Test
    void answers413ToABodyLargerThanTheLimit() {
        Map<String, Object> song = Map.of("titulo", "t".repeat(255));
        Object[] songs = Collections.nCopies(1500, song).toArray();

        StepVerifier.create(create(playlist("too-large", songs)))
                .expectNext(413)
                .verifyComplete();
        StepVerifier.create(count("SELECT COUNT(*) FROM playlist WHERE name = 'too-large'"))
                .expectNext(0L)
                .verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> count(String query) {
        return databaseClient.sql(query).map(row -> row.get(0, Long.class)).one();
    }
}
