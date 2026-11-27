package com.quipux.playlist.infrastructure.in.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequestDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "admin") String username,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "<contraseña>") String password) {

    @Override
    public String toString() {
        return "LoginRequestDto[username=" + username + "]";
    }
}
