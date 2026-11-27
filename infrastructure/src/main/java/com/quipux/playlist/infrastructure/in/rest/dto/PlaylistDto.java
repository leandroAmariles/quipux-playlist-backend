package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlaylistDto(
        @JsonProperty("nombre") String name,
        @JsonProperty("descripcion") String description,
        @JsonProperty("canciones") List<SongDto> songs) {
}
