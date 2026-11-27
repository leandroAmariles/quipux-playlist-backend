package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SongDto(
        @JsonProperty("titulo") String title,
        @JsonProperty("artista") String artist,
        @JsonProperty("album") String album,
        @JsonProperty("anno") String releaseYear,
        @JsonProperty("genero") String genre) {
}
