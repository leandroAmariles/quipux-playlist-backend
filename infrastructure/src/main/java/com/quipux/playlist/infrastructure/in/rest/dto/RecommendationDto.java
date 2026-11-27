package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.constants.RecommendationConstraints;
import io.swagger.v3.oas.annotations.media.Schema;

public record RecommendationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Vacío: la sugerencia no está verificada contra un catálogo")
        @JsonProperty("idCatalogo") String catalogId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = RecommendationConstraints.TITLE_MAX_LENGTH)
        @JsonProperty("titulo") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = RecommendationConstraints.ARTIST_MAX_LENGTH)
        @JsonProperty("artista") String artist,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = RecommendationConstraints.GENRE_MAX_LENGTH)
        @JsonProperty("genero") String genre,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = RecommendationConstraints.REASON_MAX_LENGTH)
        @JsonProperty("motivo") String reason) {
}
