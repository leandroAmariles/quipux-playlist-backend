package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.ModelChoice;
import com.quipux.playlist.domain.model.ProviderIdentity;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RecommendationProviderPort {

    ProviderIdentity identity();

    /**
     * Which candidates fit the songs, best first, and why. The choices are not verified: the domain does it. Signals
     * an error when the provider cannot answer.
     */
    Mono<List<ModelChoice>> choose(List<Song> songs, List<CatalogSong> candidates);
}
