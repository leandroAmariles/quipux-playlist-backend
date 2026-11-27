package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.enums.GenreSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GenresResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("generos") List<String> genres,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "LOCAL")
        @JsonProperty("origen") GenreSource source) {
}
