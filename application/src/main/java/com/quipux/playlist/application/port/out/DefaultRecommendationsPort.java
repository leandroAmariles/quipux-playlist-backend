package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.DefaultRecommendations;
import reactor.core.publisher.Mono;

public interface DefaultRecommendationsPort {

    Mono<DefaultRecommendations> defaults();
}
