package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetPlaylistsPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.model.Playlist;
import reactor.core.publisher.Flux;

public class GetPlaylistsUseCase implements GetPlaylistsPort {

    private final PlaylistRepositoryPort repository;

    public GetPlaylistsUseCase(PlaylistRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Playlist> getAll() {
        return repository.findAll();
    }
}
