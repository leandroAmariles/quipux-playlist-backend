package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.constants.PlaylistConstraints;
import io.swagger.v3.oas.annotations.media.Schema;

public record SongDto(
        @Schema(maxLength = PlaylistConstraints.SONG_TITLE_MAX_LENGTH, example = "Canción de ejemplo")
        @JsonProperty("titulo") String title,
        @Schema(maxLength = PlaylistConstraints.SONG_ARTIST_MAX_LENGTH, example = "Artista de ejemplo")
        @JsonProperty("artista") String artist,
        @Schema(maxLength = PlaylistConstraints.SONG_ALBUM_MAX_LENGTH, example = "Álbum de ejemplo")
        @JsonProperty("album") String album,
        @Schema(maxLength = PlaylistConstraints.SONG_RELEASE_YEAR_MAX_LENGTH, example = "1999")
        @JsonProperty("anno") String releaseYear,
        @Schema(maxLength = PlaylistConstraints.SONG_GENRE_MAX_LENGTH, example = "rock")
        @JsonProperty("genero") String genre) {
}
