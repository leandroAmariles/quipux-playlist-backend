package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetPlaylistPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Mono;

public class GetPlaylistUseCase implements GetPlaylistPort {

    private final PlaylistRepositoryPort repository;

    public GetPlaylistUseCase(PlaylistRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Playlist> getByName(String name) {
        return repository.findByName(name)
                .switchIfEmpty(Mono.error(PlaylistNotFoundException::new));
    }
}
