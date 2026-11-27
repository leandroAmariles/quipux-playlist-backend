package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Asks the model behind the gateway to choose among the candidates, with one attempt, a time limit, a circuit breaker
 * and a maximum of calls at once. No tools are registered.
 */
public class LlmRecommendationProviderAdapter implements RecommendationProviderPort {

    private static final String GENERATION_NAME = "generation_name";
    private static final String TRACE_NAME = "trace_name";
    private static final String VERSION = "version";
    private static final String SESSION = "session_id";
    private static final String TRACE = "recomendaciones";

    private final ChatModel chatModel;
    private final Mono<ActivePrompt> activePrompt;
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
        this(chatModel, Mono.just(new ActivePrompt(settings.identity().promptVersion(), prompt)), parser, settings,
                circuitBreaker);
    }

    public LlmRecommendationProviderAdapter(
            ChatModel chatModel,
            Mono<ActivePrompt> activePrompt,
            LlmResponseParser parser,
            LlmSettings settings,
            CircuitBreaker circuitBreaker) {
        this.chatModel = chatModel;
        this.activePrompt = activePrompt;
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
    public Mono<RecommendationProviderPort> current() {
        return activePrompt.map(Pinned::new);
    }

    @Override
    public Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates) {
        return activePrompt.flatMap(active -> chooseWith(active, songs, candidates));
    }

    private Mono<List<ModelChoice>> chooseWith(
            ActivePrompt active, List<Song> songs, List<CatalogSong> candidates) {
        return Mono.defer(() -> {
            CandidateAliases aliases = CandidateAliases.of(candidates);
            return Mono.fromSupplier(() -> requestFor(active, songs, aliases))
                    .flatMap(this::answerTo)
                    // Building the request loads classes and serializes: kept away from the event loop.
                    .subscribeOn(Schedulers.boundedElastic())
                    // Outside the circuit breaker: content that cannot be used does not mean the provider is down.
                    .map(content -> parser.parse(content, aliases));
        });
    }

    private Prompt requestFor(ActivePrompt active, List<Song> songs, CandidateAliases candidates) {
        PromptTemplate.Messages messages = active.template().render(settings.sample().of(songs), candidates);
        return new Prompt(
                List.of(new SystemMessage(messages.system()), new UserMessage(messages.user())),
                optionsLimitedTo(candidates, active.version()));
    }

    // What the gateway records in the trace of this call.
    private Map<String, String> traceOf(String promptVersion) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(GENERATION_NAME, TRACE);
        metadata.put(TRACE_NAME, TRACE);
        metadata.put(VERSION, promptVersion);
        if (!settings.traceSession().isBlank()) {
            metadata.put(SESSION, settings.traceSession());
        }
        return metadata;
    }

    // The options of a request replace those of the model, so they are built from them.
    private OpenAiChatOptions optionsLimitedTo(CandidateAliases candidates, String promptVersion) {
        OpenAiChatModel.ResponseFormat format = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(ChoiceSchema.of(candidates.aliases()))
                .strict(true)
                .build();
        return ((OpenAiChatOptions) chatModel.getOptions()).mutate()
                .responseFormat(format)
                .metadata(traceOf(promptVersion))
                .build();
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

    // The adapter with one prompt for the whole of a request. The limit of calls and the circuit are shared.
    private final class Pinned implements RecommendationProviderPort {

        private final ActivePrompt active;

        private Pinned(ActivePrompt active) {
            this.active = active;
        }

        @Override
        public ProviderIdentity identity() {
            return new ProviderIdentity(active.version(), settings.identity().model());
        }

        @Override
        public Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates) {
            return chooseWith(active, songs, candidates);
        }
    }
}
