package com.quipux.playlist.application.port.out;

import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaylistRepositoryPort {

    Mono<Playlist> save(Playlist playlist);


    Flux<Playlist> findAll();

    Mono<Playlist> findByName(String name);

    Mono<Boolean> deleteByName(String name);
}
