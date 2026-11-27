package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import reactor.core.publisher.Mono;

public class DeletePlaylistUseCase implements DeletePlaylistPort {

    private final PlaylistRepositoryPort repository;

    public DeletePlaylistUseCase(PlaylistRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Void> delete(String name) {
        return repository.deleteByName(name)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(PlaylistNotFoundException::new))
                .then();
    }
}
