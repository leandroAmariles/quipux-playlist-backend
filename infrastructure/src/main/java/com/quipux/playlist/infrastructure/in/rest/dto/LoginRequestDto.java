package com.quipux.playlist.infrastructure.in.rest.dto;

public record LoginRequestDto(String username, String password) {

    @Override
    public String toString() {
        return "LoginRequestDto[username=" + username + "]";
    }
}
