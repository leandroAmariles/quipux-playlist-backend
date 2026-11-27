package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.List;

public record GenresResponseDto(
        @JsonProperty("generos") List<String> genres,
        @JsonProperty("origen") GenreSource source) {
}
