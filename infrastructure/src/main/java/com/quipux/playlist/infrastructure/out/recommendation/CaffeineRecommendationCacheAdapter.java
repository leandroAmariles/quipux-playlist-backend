package com.quipux.playlist.infrastructure.out.recommendation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.quipux.playlist.application.port.out.RecommendationCachePort;
import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * A bounded cache in memory. It is lost on restart and is not shared between replicas.
 */
public class CaffeineRecommendationCacheAdapter implements RecommendationCachePort {

    private final Cache<RecommendationCacheKey, CachedRecommendations> cache;

    public CaffeineRecommendationCacheAdapter(int maxEntries, Duration timeToLive, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(timeToLive)
                .ticker(ticker)
                // The little upkeep there is happens in the calling thread.
                .executor(Runnable::run)
                .build();
    }

    @Override
    public Mono<CachedRecommendations> find(RecommendationCacheKey key) {
        return Mono.fromSupplier(() -> cache.getIfPresent(key));
    }

    @Override
    public Mono<Void> save(RecommendationCacheKey key, CachedRecommendations recommendations) {
        return Mono.fromRunnable(() -> cache.put(key, recommendations));
    }
}
