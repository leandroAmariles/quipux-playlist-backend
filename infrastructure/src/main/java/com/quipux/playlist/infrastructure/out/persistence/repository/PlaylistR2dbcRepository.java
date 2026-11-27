package com.quipux.playlist.infrastructure.out.persistence.repository;

import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistEntity;
import com.quipux.playlist.infrastructure.out.persistence.entity.PlaylistSongRow;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaylistR2dbcRepository extends ReactiveCrudRepository<PlaylistEntity, Long> {

    String SELECT_WITH_SONGS = """
            SELECT p.id AS playlist_id, p.name, p.description,
                   s.id AS song_id, s.title, s.artist, s.album, s.release_year, s.genre
            FROM playlist p
            LEFT JOIN song s ON s.playlist_id = p.id
            """;

    String CREATION_ORDER = " ORDER BY p.id, s.id";

    @Query(SELECT_WITH_SONGS + CREATION_ORDER)
    Flux<PlaylistSongRow> findAllWithSongs();

    @Query(SELECT_WITH_SONGS + " WHERE p.name = :name" + CREATION_ORDER)
    Flux<PlaylistSongRow> findWithSongsByName(String name);

    Mono<PlaylistEntity> findByName(String name);

    @Modifying
    @Query("DELETE FROM playlist WHERE id = :id")
    Mono<Long> deleteAndCountById(Long id);
}
