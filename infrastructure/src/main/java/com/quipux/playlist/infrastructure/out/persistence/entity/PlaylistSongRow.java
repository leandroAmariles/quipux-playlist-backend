package com.quipux.playlist.infrastructure.out.persistence.entity;

public record PlaylistSongRow(
        Long playlistId,
        String name,
        String description,
        Long songId,
        String title,
        String artist,
        String album,
        String releaseYear,
        String genre) {
}
