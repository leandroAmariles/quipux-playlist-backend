package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class CaffeineRecommendationCacheAdapterTest {

    private static final CachedRecommendations VALUE =
            new CachedRecommendations("v1", List.of(new Recommendation("Title", "Artist", "rock", "Reason")));

    private final AtomicLong now = new AtomicLong();
    private final CaffeineRecommendationCacheAdapter cache =
            new CaffeineRecommendationCacheAdapter(2, Duration.ofHours(24), now::get);

    @Test
    void returnsWhatWasSaved() {
        StepVerifier.create(cache.find(key("a"))).verifyComplete();

        StepVerifier.create(cache.save(key("a"), VALUE).then(cache.find(key("a"))))
                .expectNext(VALUE)
                .verifyComplete();
    }

    @Test
    void forgetsAnAnswerAfterADayEvenIfItIsRead() {
        StepVerifier.create(cache.save(key("a"), VALUE)).verifyComplete();
        now.addAndGet(Duration.ofHours(24).minusSeconds(1).toNanos());
        StepVerifier.create(cache.find(key("a"))).expectNext(VALUE).verifyComplete();

        now.addAndGet(Duration.ofSeconds(2).toNanos());

        StepVerifier.create(cache.find(key("a"))).verifyComplete();
    }

    @Test
    void holdsNoMoreThanItsMaximum() {
        StepVerifier.create(Flux.just("a", "b", "c", "d").concatMap(name -> cache.save(key(name), VALUE)))
                .verifyComplete();

        StepVerifier.create(Flux.just("a", "b", "c", "d").concatMap(name -> cache.find(key(name))).count())
                .assertNext(count -> assertThat(count).isLessThanOrEqualTo(2))
                .verifyComplete();
    }

    private static RecommendationCacheKey key(String fingerprint) {
        return new RecommendationCacheKey(fingerprint, "v1", "model");
    }
}
