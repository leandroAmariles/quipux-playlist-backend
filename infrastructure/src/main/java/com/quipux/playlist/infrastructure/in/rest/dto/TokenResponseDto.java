package com.quipux.playlist.infrastructure.in.rest.dto;

public record TokenResponseDto(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponseDto[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
