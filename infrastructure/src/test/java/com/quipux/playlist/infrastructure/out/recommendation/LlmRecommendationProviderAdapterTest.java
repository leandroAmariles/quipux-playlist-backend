package com.quipux.playlist.infrastructure.out.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class LlmRecommendationProviderAdapterTest {

    private static final List<Song> SONGS = List.of(new Song("Song", "Artist", null, null, null));
    private static final String VALID = "{\"recomendaciones\":[{\"titulo\":\"New\",\"artista\":\"Singer\"}]}";
    private static final Duration LIMIT = Duration.ofMillis(300);
    private static final Duration OPEN = Duration.ofMillis(200);

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Flux<ChatResponse>> next = new AtomicReference<>(answer(VALID));
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(OPEN)
            .permittedNumberOfCallsInHalfOpenState(1)
            .ignoreExceptions(OpenAIInvalidDataException.class)
            .build());
    private final LlmRecommendationProviderAdapter adapter = adapter(LIMIT, 2);

    @Test
    void joinsTheChunksOfTheAnswer() {
        next.set(Flux.just(chunk(VALID.substring(0, 10)), chunk(VALID.substring(10)), new ChatResponse(List.of())));

        StepVerifier.create(adapter.recommend(SONGS))
                .expectNext(List.of(new Recommendation("New", "Singer", null, null)))
                .verifyComplete();
        assertThat(adapter.identity()).isEqualTo(new ProviderIdentity("v1", "model"));
    }

    @Test
    void reportsATimeLimitOfItsOwnAndOfTheClient() {
        next.set(Flux.never());
        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT.format());

        next.set(Flux.error(new CompletionException(
                new OpenAIIoException("failed", new InterruptedIOException("timeout")))));
        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT.format());
    }

    @Test
    void reportsTheStatusOfAnErrorResponse() {
        OpenAIServiceException response = mock(OpenAIServiceException.class);
        when(response.statusCode()).thenReturn(503);
        when(response.getMessage()).thenReturn("the prompt was: secret");
        next.set(Flux.error(new CompletionException(response)));

        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_ERROR_RESPONSE.format(503));
    }

    @Test
    void reportsAConnectionFailure() {
        next.set(Flux.error(new OpenAIIoException("failed", new ConnectException("refused"))));

        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREACHABLE.format());
    }

    @Test
    void reportsUnreadableData() {
        next.set(Flux.error(new OpenAIInvalidDataException("bad chunk")));

        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREADABLE.format());
    }

    @Test
    void reportsAnEmptyAnswer() {
        next.set(Flux.empty());

        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_EMPTY.format());
    }

    @Test
    void stopsCallingAfterRepeatedFailuresAndTriesAgainLater() {
        next.set(Flux.error(new OpenAIIoException("failed", new ConnectException("refused"))));
        for (int call = 0; call < 5; call++) {
            expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_UNREACHABLE.format());
        }

        expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_CIRCUIT_OPEN.format());
        assertThat(calls).hasValue(5);

        next.set(answer(VALID));
        StepVerifier.create(Mono.delay(OPEN.multipliedBy(2)).then(adapter.recommend(SONGS)))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(calls).hasValue(6);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensAgainWhenTheTrialCallFails() {
        next.set(Flux.never());
        for (int call = 0; call < 5; call++) {
            expectReason(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT.format());
        }

        StepVerifier.create(Mono.delay(OPEN.multipliedBy(2)).then(adapter.recommend(SONGS)))
                .expectError(RecommendationProviderUnavailableException.class)
                .verify();

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(calls).hasValue(6);
    }

    @Test
    void doesNotCountInvalidContentAgainstTheProvider() {
        for (Flux<ChatResponse> content : List.of(
                answer("not json"), Flux.<ChatResponse>empty(),
                Flux.<ChatResponse>error(new OpenAIInvalidDataException("bad chunk")))) {
            next.set(content);
            for (int call = 0; call < 6; call++) {
                StepVerifier.create(adapter.recommend(SONGS))
                        .expectError(RecommendationProviderUnavailableException.class)
                        .verify();
            }
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls).hasValue(18);
    }

    @Test
    void doesNotQueueACallOverTheMaximum() {
        LlmRecommendationProviderAdapter twoAtOnce = adapter(Duration.ofSeconds(10), 2);
        Sinks.One<ChatResponse> model = Sinks.one();
        next.set(model.asMono().flux());
        CompletableFuture<List<Recommendation>> first = twoAtOnce.recommend(SONGS).toFuture();
        CompletableFuture<List<Recommendation>> second = twoAtOnce.recommend(SONGS).toFuture();
        await().until(() -> calls.get() == 2);

        StepVerifier.create(twoAtOnce.recommend(SONGS))
                .expectErrorMessage(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TOO_MANY_CALLS.format())
                .verify(Duration.ofSeconds(1));
        assertThat(calls).hasValue(2);

        model.tryEmitValue(chunk(VALID)).orThrow();
        await().until(() -> first.isDone() && second.isDone());
        next.set(answer(VALID));
        StepVerifier.create(twoAtOnce.recommend(SONGS)).expectNextCount(1).verifyComplete();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void keepsThePermitUntilTheCallEndsEvenIfNobodyWaitsForIt() {
        LlmRecommendationProviderAdapter oneAtOnce = adapter(Duration.ofSeconds(10), 1);
        Sinks.One<ChatResponse> model = Sinks.one();
        AtomicBoolean cancelled = new AtomicBoolean();
        next.set(model.asMono().flux().doOnCancel(() -> cancelled.set(true)));
        Disposable abandoned = oneAtOnce.recommend(SONGS).subscribe();
        await().until(() -> calls.get() == 1);

        abandoned.dispose();

        StepVerifier.create(oneAtOnce.recommend(SONGS))
                .expectErrorMessage(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TOO_MANY_CALLS.format())
                .verify(Duration.ofSeconds(1));
        assertThat(cancelled).isFalse();

        model.tryEmitValue(chunk(VALID)).orThrow();
        next.set(answer(VALID));
        StepVerifier.create(oneAtOnce.recommend(SONGS)).expectNextCount(1).verifyComplete();
    }

    @Test
    void returnsThePermitOfACallThatTimesOut() {
        LlmRecommendationProviderAdapter oneAtOnce = adapter(LIMIT, 1);
        next.set(Flux.never());
        StepVerifier.create(oneAtOnce.recommend(SONGS))
                .expectErrorMessage(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT.format())
                .verify(Duration.ofSeconds(5));

        next.set(answer(VALID));

        StepVerifier.create(oneAtOnce.recommend(SONGS)).expectNextCount(1).verifyComplete();
    }

    private LlmRecommendationProviderAdapter adapter(Duration timeout, int maxConcurrentCalls) {
        return new LlmRecommendationProviderAdapter(
                chatModel(),
                new PromptTemplate(new ClassPathResource("prompts/recommendations/v1.txt")),
                new LlmResponseParser(),
                new LlmSettings(new ProviderIdentity("v1", "model"), new SongSample(50, 100), timeout,
                        maxConcurrentCalls),
                circuitBreaker);
    }

    private void expectReason(String reason) {
        StepVerifier.create(adapter.recommend(SONGS))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(RecommendationProviderUnavailableException.class)
                        .hasMessage(reason)
                        .hasNoCause())
                .verify(Duration.ofSeconds(5));
    }

    private ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                calls.incrementAndGet();
                return next.get();
            }
        };
    }

    private static Flux<ChatResponse> answer(String content) {
        return Flux.just(chunk(content));
    }

    private static ChatResponse chunk(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
