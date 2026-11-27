package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.constants.PlaylistConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PlaylistDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = PlaylistConstraints.NAME_MAX_LENGTH,
                example = "Mis favoritas")
        @JsonProperty("nombre") String name,
        @Schema(maxLength = PlaylistConstraints.DESCRIPTION_MAX_LENGTH, example = "Para trabajar")
        @JsonProperty("descripcion") String description,
        @JsonProperty("canciones") List<SongDto> songs) {
}
