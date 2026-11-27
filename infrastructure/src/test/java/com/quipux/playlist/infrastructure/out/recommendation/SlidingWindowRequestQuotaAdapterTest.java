package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.model.QuotaDecision;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class SlidingWindowRequestQuotaAdapterTest {

    private final AtomicLong now = new AtomicLong();
    private final SlidingWindowRequestQuotaAdapter quota =
            new SlidingWindowRequestQuotaAdapter(10, Duration.ofMinutes(1), now::get);

    @Test
    void allowsTenRequestsAndRejectsTheEleventh() {
        consume("user", 10);

        StepVerifier.create(quota.consume("user"))
                .expectNext(QuotaDecision.reject(Duration.ofSeconds(60)))
                .verifyComplete();
    }

    @Test
    void saysHowLongUntilTheOldestRequestExpiresRoundingUp() {
        consume("user", 1);
        advance(Duration.ofSeconds(20));
        consume("user", 9);
        advance(Duration.ofMillis(500));

        StepVerifier.create(quota.consume("user"))
                .expectNext(QuotaDecision.reject(Duration.ofSeconds(40)))
                .verifyComplete();
    }

    @Test
    void neverAsksToWaitLessThanASecond() {
        consume("user", 10);
        advance(Duration.ofSeconds(60).minusMillis(1));

        StepVerifier.create(quota.consume("user"))
                .expectNext(QuotaDecision.reject(Duration.ofSeconds(1)))
                .verifyComplete();
    }

    @Test
    void servesAgainOnceTheTimeHasPassedWithoutCountingTheRejectedOnes() {
        consume("user", 10);
        advance(Duration.ofSeconds(30));
        StepVerifier.create(Flux.range(0, 20).concatMap(index -> quota.consume("user")))
                .thenConsumeWhile(decision -> !decision.allowed())
                .verifyComplete();

        advance(Duration.ofSeconds(30));

        consume("user", 10);
    }

    @Test
    void slidesTheWindowInsteadOfResettingIt() {
        consume("user", 5);
        advance(Duration.ofSeconds(40));
        consume("user", 5);
        advance(Duration.ofSeconds(25));

        consume("user", 5);
        StepVerifier.create(quota.consume("user"))
                .expectNext(QuotaDecision.reject(Duration.ofSeconds(35)))
                .verifyComplete();
    }

    @Test
    void keepsOneQuotaPerUser() {
        consume("user", 10);

        consume("admin", 10);
    }

    @Test
    void doesNotAllowMoreWhenTheRequestsArriveAtOnce() {
        Flux<QuotaDecision> decisions = Flux.range(0, 200)
                .flatMap(index -> quota.consume("user").subscribeOn(Schedulers.parallel()), 50);

        StepVerifier.create(decisions.filter(QuotaDecision::allowed).count())
                .expectNext(10L)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void doesNotStartWithALimitThatAllowsNoRequest(int limit) {
        assertThatThrownBy(() -> new SlidingWindowRequestQuotaAdapter(limit, Duration.ofMinutes(1), now::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECOMMENDATION_ERROR_003");
    }

    private void consume(String user, int times) {
        StepVerifier.create(Flux.range(0, times).concatMap(index -> quota.consume(user)))
                .thenConsumeWhile(QuotaDecision::allowed)
                .verifyComplete();
    }

    private void advance(Duration duration) {
        now.addAndGet(duration.toNanos());
    }
}
