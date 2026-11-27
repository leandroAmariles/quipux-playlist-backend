package com.quipux.playlist.infrastructure.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Bearer") String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1800") long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponseDto[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
