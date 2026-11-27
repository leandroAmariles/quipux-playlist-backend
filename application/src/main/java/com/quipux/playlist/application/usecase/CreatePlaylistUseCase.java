package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Mono;

public class CreatePlaylistUseCase implements CreatePlaylistPort {

    private final PlaylistRepositoryPort repository;

    public CreatePlaylistUseCase(PlaylistRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Playlist> create(Playlist playlist) {
        return repository.save(playlist);
    }
}
