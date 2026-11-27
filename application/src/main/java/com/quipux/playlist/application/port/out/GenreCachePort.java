package com.quipux.playlist.application.port.out;

import java.util.List;
import reactor.core.publisher.Mono;

public interface GenreCachePort {

    Mono<List<String>> find();

    Mono<Void> save(List<String> genres);
}
