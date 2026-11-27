package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.EvaluatedPlaylist;
import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Mono;

public interface EvaluateRecommendationsPort {

    /**
     * Recommends for a playlist that is not stored, and tells which candidates the model had. It is not part of the
     * public API.
     */
    Mono<EvaluatedPlaylist> evaluate(Playlist playlist);
}
