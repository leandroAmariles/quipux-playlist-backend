package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Asks the model behind the gateway for suggestions, with one attempt, a time limit, a circuit breaker and a
 * maximum of calls at once. No tools are registered.
 */
public class LlmRecommendationProviderAdapter implements RecommendationProviderPort {

    private final ChatModel chatModel;
    private final PromptTemplate prompt;
    private final LlmResponseParser parser;
    private final LlmSettings settings;
    private final CircuitBreaker circuitBreaker;
    private final Semaphore permits;

    public LlmRecommendationProviderAdapter(
            ChatModel chatModel,
            PromptTemplate prompt,
            LlmResponseParser parser,
            LlmSettings settings,
            CircuitBreaker circuitBreaker) {
        this.chatModel = chatModel;
        this.prompt = prompt;
        this.parser = parser;
        this.settings = settings;
        this.circuitBreaker = circuitBreaker;
        this.permits = new Semaphore(settings.maxConcurrentCalls());
    }

    @Override
    public ProviderIdentity identity() {
        return settings.identity();
    }

    @Override
    public Mono<List<Recommendation>> recommend(List<Song> songs) {
        return Mono.fromSupplier(() -> requestFor(songs))
                .flatMap(this::answerTo)
                // Building the request loads classes and serializes: kept away from the event loop.
                .subscribeOn(Schedulers.boundedElastic())
                // Outside the circuit breaker: content that cannot be used does not mean the provider is down.
                .map(parser::parse);
    }

    private Prompt requestFor(List<Song> songs) {
        PromptTemplate.Messages messages = prompt.render(settings.sample().of(songs));
        return new Prompt(new SystemMessage(messages.system()), new UserMessage(messages.user()));
    }

    // A call over the maximum does not wait for its turn.
    private Mono<String> answerTo(Prompt request) {
        if (!permits.tryAcquire()) {
            return Mono.error(new RecommendationProviderUnavailableException(
                    ErrorMessageEnum.RECOMMENDATION_PROVIDER_TOO_MANY_CALLS));
        }
        // Deferred, so that an open circuit does not even reach the model.
        return Flux.defer(() -> chatModel.stream(request))
                .map(LlmRecommendationProviderAdapter::textOf)
                .collect(Collectors.joining())
                // The client has the same limit and is the one that closes the connection; this one guarantees an
                // answer. The circuit breaker goes after it, so that it counts both.
                .timeout(settings.timeout())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(ProviderFailures::translate)
                .doOnTerminate(permits::release)
                // Cancelling does not close the connection: the call goes on, and keeps its permit until it ends,
                // even if nobody waits for it any more.
                .cache();
    }

    // The chunk that only reports the usage carries no result.
    private static String textOf(ChatResponse chunk) {
        return chunk.getResult() == null ? "" : Objects.toString(chunk.getResult().getOutput().getText(), "");
    }
}
