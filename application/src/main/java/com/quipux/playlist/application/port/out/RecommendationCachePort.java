package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.CachedRecommendations;
import com.quipux.playlist.domain.model.RecommendationCacheKey;
import reactor.core.publisher.Mono;

public interface RecommendationCachePort {

    Mono<CachedRecommendations> find(RecommendationCacheKey key);

    Mono<Void> save(RecommendationCacheKey key, CachedRecommendations recommendations);
}
