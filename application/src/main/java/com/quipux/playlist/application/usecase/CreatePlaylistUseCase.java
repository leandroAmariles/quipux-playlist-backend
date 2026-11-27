package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import reactor.core.publisher.Mono;

public class CreatePlaylistUseCase implements CreatePlaylistPort {

    private final PlaylistRepositoryPort repository;
    private final GetGenresPort getGenres;

    public CreatePlaylistUseCase(PlaylistRepositoryPort repository, GetGenresPort getGenres) {
        this.repository = repository;
        this.getGenres = getGenres;
    }

    @Override
    public Mono<Playlist> create(Playlist playlist) {
        List<String> genres = playlist.declaredGenres();
        if (genres.isEmpty()) {
            return repository.save(playlist);
        }
        return getGenres.getGenres()
                .flatMap(catalog -> catalog.includesAll(genres)
                        ? repository.save(playlist)
                        : Mono.error(new InvalidPlaylistException(ErrorMessageEnum.UNKNOWN_GENRE)));
    }
}
