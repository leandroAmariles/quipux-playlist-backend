package com.quipux.playlist.infrastructure.out.genre.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpotifyTokenDto(@JsonProperty("access_token") String accessToken) {

    @Override
    public String toString() {
        return "SpotifyTokenDto[]";
    }
}
