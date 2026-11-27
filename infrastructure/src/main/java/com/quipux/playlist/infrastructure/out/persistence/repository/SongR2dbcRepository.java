package com.quipux.playlist.infrastructure.out.persistence.repository;

import com.quipux.playlist.infrastructure.out.persistence.entity.SongEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface SongR2dbcRepository extends ReactiveCrudRepository<SongEntity, Long> {

    @Modifying
    @Query("DELETE FROM song WHERE playlist_id = :playlistId")
    Mono<Long> deleteByPlaylistId(Long playlistId);
}
