package com.quipux.playlist.infrastructure.out.recommendation.config;

import com.github.benmanes.caffeine.cache.Ticker;
import com.openai.errors.OpenAIInvalidDataException;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.infrastructure.out.recommendation.ActivePrompt;
import com.quipux.playlist.infrastructure.out.recommendation.LangfusePromptSource;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.genre.LocalGenreProviderAdapter;
import com.quipux.playlist.infrastructure.out.recommendation.CaffeineRecommendationCacheAdapter;
import com.quipux.playlist.infrastructure.out.recommendation.FileDefaultRecommendationsAdapter;
import com.quipux.playlist.infrastructure.out.recommendation.LlmRecommendationProviderAdapter;
import com.quipux.playlist.infrastructure.out.recommendation.LlmResponseParser;
import com.quipux.playlist.infrastructure.out.recommendation.LlmSettings;
import com.quipux.playlist.infrastructure.out.recommendation.PromptTemplate;
import com.quipux.playlist.infrastructure.out.recommendation.SimulatedRecommendationProviderAdapter;
import com.quipux.playlist.infrastructure.out.recommendation.SingleAttemptInterceptor;
import com.quipux.playlist.infrastructure.out.recommendation.SlidingWindowRequestQuotaAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
public class RecommendationConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationConfig.class);
    private static final String CIRCUIT_NAME = "recommendation-provider";

    @Value("${playlist.ai.default-recommendations-file:classpath:default-recommendations.properties}")
    private Resource defaultRecommendationsFile;

    @Value("${playlist.genres.local-file:classpath:local-genres.properties}")
    private Resource localGenres;

    @Value("${playlist.ai.prompt-version:v2}")
    private String promptVersion;

    @Value("classpath:prompts/recommendations/${playlist.ai.prompt-version:v2}.txt")
    private Resource promptFile;

    @Value("${playlist.ai.chat-model:recomendador-chat}")
    private String chatModelAlias;

    @Value("${playlist.ai.max-songs:50}")
    private int maxSongs;

    @Value("${playlist.ai.max-field-length:100}")
    private int maxFieldLength;

    @Value("${playlist.ai.timeout:30s}")
    private Duration timeout;

    @Value("${playlist.ai.circuit-breaker.window:10}")
    private int circuitWindow;

    @Value("${playlist.ai.circuit-breaker.minimum-calls:5}")
    private int circuitMinimumCalls;

    @Value("${playlist.ai.circuit-breaker.failure-rate:50}")
    private float circuitFailureRate;

    @Value("${playlist.ai.circuit-breaker.open-duration:30s}")
    private Duration circuitOpenDuration;

    @Value("${playlist.ai.max-concurrent-calls:2}")
    private int maxConcurrentCalls;

    @Value("${playlist.ai.requests-per-minute:10}")
    private int requestsPerMinute;

    @Value("${playlist.ai.cache.ttl:24h}")
    private Duration cacheTimeToLive;

    @Value("${playlist.ai.cache.max-entries:500}")
    private int cacheMaxEntries;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${playlist.ai.trace-session:}")
    private String traceSession;

    @Value("${playlist.ai.langfuse.host:}")
    private String langfuseHost;

    @Value("${playlist.ai.langfuse.public-key:}")
    private String langfusePublicKey;

    @Value("${playlist.ai.langfuse.secret-key:}")
    private String langfuseSecretKey;

    @Value("${playlist.ai.langfuse.prompt-name:recomendaciones-v2}")
    private String langfusePromptName;

    @Value("${playlist.ai.langfuse.timeout:1s}")
    private Duration langfuseTimeout;

    @Value("${playlist.ai.langfuse.cache-ttl:60s}")
    private Duration langfuseCacheTimeToLive;

    @Bean
    DefaultRecommendationsPort defaultRecommendations() {
        return new FileDefaultRecommendationsAdapter(
                defaultRecommendationsFile, new LocalGenreProviderAdapter(localGenres).catalog());
    }

    @Bean
    RecommendationCachePort recommendationCache() {
        return new CaffeineRecommendationCacheAdapter(cacheMaxEntries, cacheTimeToLive, Ticker.systemTicker());
    }

    @Bean
    RequestQuotaPort requestQuota() {
        return new SlidingWindowRequestQuotaAdapter(requestsPerMinute, Duration.ofMinutes(1), Ticker.systemTicker());
    }

    @Bean
    OpenAiHttpClientBuilderCustomizer singleAttempt() {
        return client -> client.interceptor(new SingleAttemptInterceptor());
    }

    // Spring AI only creates a chat model when the AI is enabled.
    @Bean
    RecommendationProviderPort recommendationProvider(ObjectProvider<ChatModel> chatModels) {
        PromptTemplate prompt = new PromptTemplate(promptFile);
        ProviderIdentity identity = new ProviderIdentity(promptVersion, chatModelAlias);
        ChatModel chatModel = chatModels.getIfAvailable();
        if (chatModel == null) {
            return new SimulatedRecommendationProviderAdapter(
                    ErrorMessageEnum.RECOMMENDATION_PROVIDER_DISABLED, identity);
        }
        if (apiKey.isBlank()) {
            LOG.warn(ErrorMessageEnum.RECOMMENDATION_PROVIDER_NOT_CONFIGURED.withCode());
            return new SimulatedRecommendationProviderAdapter(
                    ErrorMessageEnum.RECOMMENDATION_PROVIDER_NOT_CONFIGURED, identity);
        }
        return new LlmRecommendationProviderAdapter(chatModel,
                activePrompt(new ActivePrompt(promptVersion, prompt)), new LlmResponseParser(),
                new LlmSettings(identity, new SongSample(maxSongs, maxFieldLength), timeout, maxConcurrentCalls,
                        traceSession),
                circuitBreaker());
    }

    // Without an address and both keys, Langfuse is not asked: the prompt is the one of the file.
    private Mono<ActivePrompt> activePrompt(ActivePrompt local) {
        if (langfuseHost.isBlank() || langfusePublicKey.isBlank() || langfuseSecretKey.isBlank()) {
            return Mono.just(local);
        }
        WebClient client = WebClient.builder()
                .baseUrl(langfuseHost)
                .defaultHeaders(headers -> headers.setBasicAuth(langfusePublicKey, langfuseSecretKey))
                .build();
        return new LangfusePromptSource(
                client, langfusePromptName, local, langfuseTimeout, langfuseCacheTimeToLive)
                .active();
    }

    // Timeouts, connection failures and error responses count; data that cannot be read does not.
    private CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of(CIRCUIT_NAME, CircuitBreakerConfig.custom()
                .slidingWindowSize(circuitWindow)
                .minimumNumberOfCalls(circuitMinimumCalls)
                .failureRateThreshold(circuitFailureRate)
                .waitDurationInOpenState(circuitOpenDuration)
                .permittedNumberOfCallsInHalfOpenState(1)
                .ignoreExceptions(OpenAIInvalidDataException.class)
                .build());
    }
}
