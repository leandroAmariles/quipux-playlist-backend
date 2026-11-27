package com.quipux.playlist.infrastructure.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quipux.playlist.domain.model.constants.PlaylistConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RecommendationsResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = PlaylistConstraints.NAME_MAX_LENGTH)
        @JsonProperty("lista") String playlistName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"IA", "RESPALDO"},
                example = "RESPALDO")
        @JsonProperty("origen") String origin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Vacío si el origen no es IA")
        @JsonProperty("versionPrompt") String promptVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("recomendaciones") List<RecommendationDto> recommendations) {
}
