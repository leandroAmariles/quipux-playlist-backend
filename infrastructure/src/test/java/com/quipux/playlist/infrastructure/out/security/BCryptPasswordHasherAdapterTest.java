package com.quipux.playlist.infrastructure.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

class BCryptPasswordHasherAdapterTest {

    private final BCryptPasswordHasherAdapter hasher = new BCryptPasswordHasherAdapter();

    @Test
    void hashesWithBCryptAndMatchesOnlyTheSamePassword() {
        Mono<Tuple2<Boolean, Boolean>> matches = hasher.hash("password")
                .doOnNext(hash -> assertThat(hash).startsWith("$2").hasSize(60).doesNotContain("password"))
                .flatMap(hash -> Mono.zip(hasher.matches("password", hash), hasher.matches("other", hash)));

        StepVerifier.create(matches)
                .assertNext(result -> {
                    assertThat(result.getT1()).isTrue();
                    assertThat(result.getT2()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void worksOutsideTheCallingThread() {
        StepVerifier.create(hasher.hash("password").map(hash -> Thread.currentThread().getName()))
                .assertNext(thread -> assertThat(thread).startsWith("boundedElastic"))
                .verifyComplete();
    }
}
