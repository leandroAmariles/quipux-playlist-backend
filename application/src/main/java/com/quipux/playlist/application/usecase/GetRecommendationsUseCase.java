package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetRecommendationsPort;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.RequestQuotaPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.exception.RecommendationRateLimitException;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import com.quipux.playlist.domain.model.RecommendationRules;
import com.quipux.playlist.domain.model.RecommendationSet;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GetRecommendationsUseCase implements GetRecommendationsPort {

    private static final Logger LOG = Loggers.getLogger(GetRecommendationsUseCase.class);
    // Never the songs, the prompt or who asks.
    private static final String SERVED = "Recommendations served: origin={}, cached={}, model={}, "
            + "promptVersion={}, catalogVersion={}, candidates={}, retrievalMs={}, latencyMs={}";

    private final PlaylistRepositoryPort repository;
    private final RecommendationProviderPort provider;
    private final SongCatalogPort catalog;
    private final DefaultRecommendationsPort defaultRecommendations;
    private final RecommendationCachePort cache;
    private final RequestQuotaPort quota;
    private final int maxCandidates;
    private final Duration timeout;

    public GetRecommendationsUseCase(
            PlaylistRepositoryPort repository,
            RecommendationProviderPort provider,
            SongCatalogPort catalog,
            DefaultRecommendationsPort defaultRecommendations,
            RecommendationCachePort cache,
            RequestQuotaPort quota,
            int maxCandidates,
            Duration timeout) {
        this.repository = repository;
        this.provider = provider;
        this.catalog = catalog;
        this.defaultRecommendations = defaultRecommendations;
        this.cache = cache;
        this.quota = quota;
        this.maxCandidates = maxCandidates;
        this.timeout = timeout;
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
                    .doOnNext(answered -> record(answered, start))
                    .map(answered -> answered.served().answer());
        });
    }

    private Mono<Void> countRequestOf(String requester) {
        return quota.consume(requester)
                .flatMap(decision -> decision.allowed()
                        ? Mono.<Void>empty()
                        : Mono.error(new RecommendationRateLimitException(decision.retryAfter().toSeconds())));
    }

    private Mono<Answered> recommendFor(Playlist playlist) {
        if (playlist.songs().isEmpty()) {
            return fallbackFor(playlist, ErrorMessageEnum.RECOMMENDATION_EMPTY_PLAYLIST.format(), 0, 0)
                    .map(served -> new Answered(served, provider.identity()));
        }
        // The same provider for the whole request: the key, the prompt and the version of the answer match.
        return provider.current()
                .flatMap(current -> cachedOrAsked(current, playlist)
                        .map(served -> new Answered(served, current.identity())));
    }

    private Mono<Served> cachedOrAsked(RecommendationProviderPort current, Playlist playlist) {
        RecommendationCacheKey key = RecommendationCacheKey.of(
                playlist.songs(), current.identity(), catalog.catalog().version());
        return cache.find(key)
                .map(saved -> new Served(RecommendationSet.fromAi(
                        playlist.name(), saved.promptVersion(), saved.recommendations()), true, 0, 0))
                .switchIfEmpty(Mono.defer(() -> askAndSave(current, playlist, key)));
    }

    // The ladder: candidates and a model that chooses; the nearest candidates if the model gives nothing; the default
    // list if there are no candidates. Only an answer of the model is saved: after a failure the next request tries
    // again.
    private Mono<Served> askAndSave(
            RecommendationProviderPort current, Playlist playlist, RecommendationCacheKey key) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return candidatesFor(playlist)
                    .flatMap(candidates -> chooseAmong(current, playlist, key, candidates, start))
                    // Only the search can still fail here.
                    .onErrorResume(error -> fallbackFor(playlist, reasonOf(error), 0, millisSince(start)));
        });
    }

    // The time limit covers the search and the model: the model has what the search left.
    private Mono<Served> chooseAmong(
            RecommendationProviderPort current,
            Playlist playlist,
            RecommendationCacheKey key,
            List<CatalogSong> candidates,
            long start) {
        long retrievalMillis = millisSince(start);
        Duration remaining = timeout.minusNanos(System.nanoTime() - start);
        return askTheProvider(current, playlist, candidates, remaining)
                .flatMap(answer -> cache
                        .save(key, new CachedRecommendations(answer.promptVersion(), answer.recommendations()))
                        .thenReturn(new Served(answer, false, candidates.size(), retrievalMillis)))
                .onErrorResume(error -> Mono.just(
                        similarTo(playlist, candidates, reasonOf(error), retrievalMillis)));
    }

    // Without candidates the model is not asked: it never recommends anything that is not verified.
    private Mono<List<CatalogSong>> candidatesFor(Playlist playlist) {
        return Mono.defer(() -> {
            SongCatalog current = catalog.catalog();
            // As many more as songs of the catalog are in the playlist, so that enough remain after leaving them out.
            return catalog.nearest(playlist.songs(), maxCandidates + current.countOf(playlist.songs()))
                    .map(nearest -> RecommendationRules.candidates(
                            nearest, current, playlist.songs(), maxCandidates))
                    .filter(candidates -> !candidates.isEmpty())
                    .switchIfEmpty(Mono.error(() -> new SongCatalogUnavailableException(
                            ErrorMessageEnum.RECOMMENDATION_NO_CANDIDATES)));
        });
    }

    private Mono<RecommendationSet> askTheProvider(
            RecommendationProviderPort current,
            Playlist playlist,
            List<CatalogSong> candidates,
            Duration remaining) {
        if (remaining.isNegative() || remaining.isZero()) {
            return Mono.error(outOfTime());
        }
        return Mono.defer(() -> current.choose(playlist.songs(), candidates))
                .timeout(remaining, Mono.error(this::outOfTime))
                .map(choices -> RecommendationRules.fromChoices(choices, candidates))
                .filter(chosen -> !chosen.isEmpty())
                .switchIfEmpty(Mono.error(() -> new RecommendationProviderUnavailableException(
                        ErrorMessageEnum.RECOMMENDATION_NO_VALID_SUGGESTIONS)))
                .map(chosen -> RecommendationSet.fromAi(
                        playlist.name(), current.identity().promptVersion(), chosen));
    }

    private RecommendationProviderUnavailableException outOfTime() {
        return new RecommendationProviderUnavailableException(ErrorMessageEnum.RECOMMENDATION_PROVIDER_TIMEOUT);
    }

    private Served similarTo(
            Playlist playlist, List<CatalogSong> candidates, String reason, long retrievalMillis) {
        LOG.warn(ErrorMessageEnum.RECOMMENDATION_SIMILARITY.withCode(reason));
        return new Served(
                RecommendationSet.similar(playlist.name(), RecommendationRules.similar(candidates)),
                false, candidates.size(), retrievalMillis);
    }

    private Mono<Served> fallbackFor(Playlist playlist, String reason, int candidates, long retrievalMillis) {
        LOG.warn(ErrorMessageEnum.RECOMMENDATION_FALLBACK.withCode(reason));
        return defaultRecommendations.defaults()
                .map(defaults -> new Served(
                        RecommendationSet.fallback(
                                playlist.name(), RecommendationRules.fallbackFor(defaults, playlist.songs())),
                        false, candidates, retrievalMillis));
    }

    private void record(Answered answered, long start) {
        Served served = answered.served();
        LOG.info(SERVED, served.answer().origin().externalValue(), served.cached(), answered.identity().model(),
                answered.identity().promptVersion(), catalog.catalog().version(), served.candidates(),
                served.retrievalMillis(), millisSince(start));
    }

    private static long millisSince(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    // Only our own messages are recorded: the text of any other error is not under our control.
    private static String reasonOf(Throwable error) {
        return error instanceof BusinessException ? error.getMessage() : error.getClass().getSimpleName();
    }

    // What was answered and how it was obtained, for the record.
    private record Served(RecommendationSet answer, boolean cached, int candidates, long retrievalMillis) {
    }

    // The same, with the model and prompt that were in force for the request.
    private record Answered(Served served, ProviderIdentity identity) {
    }
}
