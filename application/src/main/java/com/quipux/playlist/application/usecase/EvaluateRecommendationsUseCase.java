package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.EvaluateRecommendationsPort;
import com.quipux.playlist.application.port.out.DefaultRecommendationsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.EvaluatedPlaylist;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.QuotaDecision;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import com.quipux.playlist.domain.model.Song;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs the same steps as a request of recommendations, so that what is measured is what is served: the playlist is
 * only in memory, nothing is cached or counted, and what the provider receives is recorded.
 */
public class EvaluateRecommendationsUseCase implements EvaluateRecommendationsPort {

    private static final String REQUESTER = "evaluation";

    private final RecommendationProviderPort provider;
    private final SongCatalogPort catalog;
    private final DefaultRecommendationsPort defaultRecommendations;
    private final int maxCandidates;
    private final Duration timeout;

    public EvaluateRecommendationsUseCase(
            RecommendationProviderPort provider,
            SongCatalogPort catalog,
            DefaultRecommendationsPort defaultRecommendations,
            int maxCandidates,
            Duration timeout) {
        this.provider = provider;
        this.catalog = catalog;
        this.defaultRecommendations = defaultRecommendations;
        this.maxCandidates = maxCandidates;
        this.timeout = timeout;
    }

    @Override
    public Mono<EvaluatedPlaylist> evaluate(Playlist playlist) {
        return provider.current().flatMap(current -> {
            AtomicReference<List<CatalogSong>> offered = new AtomicReference<>(List.of());
            return new GetRecommendationsUseCase(
                    new OnlyPlaylist(playlist), new Recording(current, offered), catalog, defaultRecommendations,
                    new NoCache(), requester -> Mono.just(QuotaDecision.permit()), maxCandidates, timeout)
                    .recommend(REQUESTER, playlist.name())
                    .map(answer -> new EvaluatedPlaylist(answer, offered.get(), current.identity()));
        });
    }

    private record Recording(RecommendationProviderPort provider, AtomicReference<List<CatalogSong>> offered)
            implements RecommendationProviderPort {

        @Override
        public ProviderIdentity identity() {
            return provider.identity();
        }

        @Override
        public Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates) {
            offered.set(candidates);
            return provider.choose(songs, candidates);
        }
    }

    private record OnlyPlaylist(Playlist playlist) implements PlaylistRepositoryPort {

        @Override
        public Mono<Playlist> findByName(String name) {
            return Mono.just(playlist);
        }

        @Override
        public Flux<Playlist> findAll() {
            return Flux.just(playlist);
        }

        @Override
        public Mono<Playlist> save(Playlist other) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Boolean> deleteByName(String name) {
            return Mono.error(new UnsupportedOperationException());
        }
    }

    private static final class NoCache implements RecommendationCachePort {

        @Override
        public Mono<CachedRecommendations> find(RecommendationCacheKey key) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> save(RecommendationCacheKey key, CachedRecommendations recommendations) {
            return Mono.empty();
        }
    }
}
