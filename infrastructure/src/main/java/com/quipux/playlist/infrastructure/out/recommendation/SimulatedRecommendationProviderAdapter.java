package com.quipux.playlist.infrastructure.out.recommendation;

import com.quipux.playlist.application.port.out.RecommendationProviderPort;
import com.quipux.playlist.domain.exception.RecommendationProviderUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Stands in for the model when the AI is disabled or has no key: it never answers, so the default list is used.
 */
public class SimulatedRecommendationProviderAdapter implements RecommendationProviderPort {

    private final ErrorMessageEnum reason;
    private final ProviderIdentity identity;

    public SimulatedRecommendationProviderAdapter(ErrorMessageEnum reason, ProviderIdentity identity) {
        this.reason = reason;
        this.identity = identity;
    }

    @Override
    public ProviderIdentity identity() {
        return identity;
    }

    @Override
    public Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates) {
        return Mono.error(() -> new RecommendationProviderUnavailableException(reason));
    }
}
