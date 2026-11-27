package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Recommendation;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RecommendationProviderPort {

    ProviderIdentity identity();

    /**
     * Raw suggestions for the songs. Signals an error when the provider cannot answer.
     */
    Mono<List<Recommendation>> recommend(List<Song> songs);
}
