package com.quipux.playlist.infrastructure.out.event;

import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.domain.model.PlaylistEvent;
import reactor.core.publisher.Mono;

public class NoOpPlaylistEventPublisherAdapter implements PlaylistEventPublisherPort {

    @Override
    public Mono<Void> publish(PlaylistEvent event) {
        return Mono.empty();
    }
}
