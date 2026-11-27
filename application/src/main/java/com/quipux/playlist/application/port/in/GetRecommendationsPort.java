package com.quipux.playlist.application.port.in;

import com.quipux.playlist.domain.model.RecommendationSet;
import reactor.core.publisher.Mono;

public interface GetRecommendationsPort {

    Mono<RecommendationSet> recommend(String requester, String playlistName);
}
