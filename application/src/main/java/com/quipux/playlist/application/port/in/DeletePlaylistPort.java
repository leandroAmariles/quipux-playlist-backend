package com.quipux.playlist.application.port.in;

import reactor.core.publisher.Mono;

public interface DeletePlaylistPort {

    Mono<Void> delete(String name);
}
