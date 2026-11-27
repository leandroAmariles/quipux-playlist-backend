package com.quipux.playlist.infrastructure.out.genre;

import com.quipux.playlist.application.port.out.GenreCachePort;
import java.util.List;
import reactor.core.publisher.Mono;

public class NoOpGenreCacheAdapter implements GenreCachePort {

    @Override
    public Mono<List<String>> find() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> save(List<String> genres) {
        return Mono.empty();
    }
}
