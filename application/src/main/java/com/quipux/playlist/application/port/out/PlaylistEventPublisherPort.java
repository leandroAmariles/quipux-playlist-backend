package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.PlaylistEvent;
import reactor.core.publisher.Mono;

public interface PlaylistEventPublisherPort {

    Mono<Void> publish(PlaylistEvent event);
}
