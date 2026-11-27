package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetRecommendationsPort;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.exception.RecommendationRateLimitException;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import com.quipux.playlist.domain.model.RecommendationRules;
import com.quipux.playlist.domain.model.RecommendationSet;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GetRecommendationsUseCase implements GetRecommendationsPort {

    private static final Logger LOG = Loggers.getLogger(GetRecommendationsUseCase.class);
    // Never the songs, the prompt or who asks.
    private static final String SERVED =
            "Recommendations served: origin={}, cached={}, model={}, promptVersion={}, latencyMs={}";

    private final PlaylistRepositoryPort repository;
    private final RecommendationProviderPort provider;
    private final DefaultRecommendationsPort defaultRecommendations;
    private final RecommendationCachePort cache;
    private final RequestQuotaPort quota;

    public GetRecommendationsUseCase(
            PlaylistRepositoryPort repository,
            RecommendationProviderPort provider,
            DefaultRecommendationsPort defaultRecommendations,
            RecommendationCachePort cache,
            RequestQuotaPort quota) {
        this.repository = repository;
        this.provider = provider;
        this.defaultRecommendations = defaultRecommendations;
        this.cache = cache;
        this.quota = quota;
    }

    @Override
    public Mono<RecommendationSet> recommend(String requester, String playlistName) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            // The quota goes first: asking for a playlist that does not exist also spends it.
            return countRequestOf(requester)
                    .then(Mono.defer(() -> repository.findByName(playlistName)))
                    .switchIfEmpty(Mono.error(PlaylistNotFoundException::new))
                    .flatMap(this::recommendFor)
                    .doOnNext(served -> record(served, start))
                    .map(Served::answer);
        });
    }

    private Mono<Void> countRequestOf(String requester) {
        return quota.consume(requester)
                .flatMap(decision -> decision.allowed()
                        ? Mono.<Void>empty()
                        : Mono.error(new RecommendationRateLimitException(decision.retryAfter().toSeconds())));
    }

    private Mono<Served> recommendFor(Playlist playlist) {
        if (playlist.songs().isEmpty()) {
            return fallbackFor(playlist, ErrorMessageEnum.RECOMMENDATION_EMPTY_PLAYLIST.format());
        }
        RecommendationCacheKey key = RecommendationCacheKey.of(playlist.songs(), provider.identity());
        return cache.find(key)
                .map(saved -> new Served(RecommendationSet.fromAi(
                        playlist.name(), saved.promptVersion(), saved.recommendations()), true))
                .switchIfEmpty(Mono.defer(() -> askAndSave(playlist, key)));
    }

    // Only an answer of the model is saved: after a failure the next request tries again.
    private Mono<Served> askAndSave(Playlist playlist, RecommendationCacheKey key) {
        return askTheProvider(playlist)
                .flatMap(answer -> cache
                        .save(key, new CachedRecommendations(answer.promptVersion(), answer.recommendations()))
                        .thenReturn(new Served(answer, false)))
                .onErrorResume(error -> fallbackFor(playlist, reasonOf(error)));
    }

    private Mono<RecommendationSet> askTheProvider(Playlist playlist) {
        return Mono.defer(() -> provider.recommend(playlist.songs()))
                .map(suggestions -> RecommendationRules.validate(suggestions, playlist.songs()))
                .filter(valid -> !valid.isEmpty())
                .switchIfEmpty(Mono.error(() -> new RecommendationProviderUnavailableException(
                        ErrorMessageEnum.RECOMMENDATION_NO_VALID_SUGGESTIONS)))
                .map(valid -> RecommendationSet.fromAi(
                        playlist.name(), provider.identity().promptVersion(), valid));
    }

    private Mono<Served> fallbackFor(Playlist playlist, String reason) {
        LOG.warn(ErrorMessageEnum.RECOMMENDATION_FALLBACK.withCode(reason));
        return defaultRecommendations.defaults()
                .map(defaults -> new Served(RecommendationSet.fallback(
                        playlist.name(), RecommendationRules.fallbackFor(defaults, playlist.songs())), false));
    }

    private void record(Served served, long start) {
        LOG.info(SERVED, served.answer().origin().externalValue(), served.cached(), provider.identity().model(),
                provider.identity().promptVersion(), Duration.ofNanos(System.nanoTime() - start).toMillis());
    }

    // Only our own messages are recorded: the text of any other error is not under our control.
    private static String reasonOf(Throwable error) {
        return error instanceof BusinessException ? error.getMessage() : error.getClass().getSimpleName();
    }

    private record Served(RecommendationSet answer, boolean cached) {
    }
}
