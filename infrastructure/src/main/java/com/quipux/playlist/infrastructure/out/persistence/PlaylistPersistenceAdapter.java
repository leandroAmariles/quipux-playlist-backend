package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistEntity;
import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistSongRow;
import com.quipux.playlist.infrastructure.out.persistence.mapper.PlaylistEntityMapper;
import com.quipux.playlist.infrastructure.out.persistence.repository.PlaylistR2dbcRepository;
import com.quipux.playlist.infrastructure.out.persistence.repository.SongR2dbcRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PlaylistPersistenceAdapter implements PlaylistRepositoryPort {

    private final PlaylistR2dbcRepository playlistRepository;
    private final SongR2dbcRepository songRepository;
    private final PlaylistEntityMapper mapper;
    private final TransactionalOperator transactionalOperator;

    public PlaylistPersistenceAdapter(
            PlaylistR2dbcRepository playlistRepository,
            SongR2dbcRepository songRepository,
            PlaylistEntityMapper mapper,
            TransactionalOperator transactionalOperator) {
        this.playlistRepository = playlistRepository;
        this.songRepository = songRepository;
        this.mapper = mapper;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<Playlist> save(Playlist playlist) {
        return playlistRepository.save(mapper.toEntity(playlist))
                .onErrorMap(DuplicateKeyException.class, error -> new DuplicatePlaylistException())
                .flatMap(saved -> saveSongs(playlist, saved))
                .thenReturn(playlist)
                .as(transactionalOperator::transactional);
    }

    @Override
    public Flux<Playlist> findAll() {
        return toPlaylists(playlistRepository.findAllWithSongs());
    }

    @Override
    public Mono<Playlist> findByName(String name) {
        return toPlaylists(playlistRepository.findWithSongsByName(name)).singleOrEmpty();
    }

    @Override
    public Mono<Boolean> deleteByName(String name) {
        return playlistRepository.findByName(name)
                .flatMap(playlist -> songRepository.deleteByPlaylistId(playlist.id())
                        .then(playlistRepository.deleteAndCountById(playlist.id())))
                .map(deletedPlaylists -> deletedPlaylists > 0)
                .defaultIfEmpty(false)
                .as(transactionalOperator::transactional);
    }

    private Flux<Playlist> toPlaylists(Flux<PlaylistSongRow> rows) {
        return rows.bufferUntilChanged(PlaylistSongRow::playlistId)
                .map(mapper::toPlaylist);
    }

    private Mono<Void> saveSongs(Playlist playlist, PlaylistEntity saved) {
        // concatMap inserts one song at a time, so their ids keep the order in which they were sent.
        return Flux.fromIterable(playlist.songs())
                .map(song -> mapper.toEntity(song, saved.id()))
                .concatMap(songRepository::save)
                .then();
    }
}
