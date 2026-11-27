package com.quipux.playlist.infrastructure.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("SONG")
public record SongEntity(
        @Id Long id,
        Long playlistId,
        String title,
        String artist,
        String album,
        String releaseYear,
        String genre) {
}
