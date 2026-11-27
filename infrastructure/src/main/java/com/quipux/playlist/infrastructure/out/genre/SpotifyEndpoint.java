package com.quipux.playlist.infrastructure.out.genre;

public enum SpotifyEndpoint {

    TOKEN("/api/token", "token"),
    GENRE_SEEDS("/v1/recommendations/available-genre-seeds", "genres");

    private final String path;
    private final String requestName;

    SpotifyEndpoint(String path, String requestName) {
        this.path = path;
        this.requestName = requestName;
    }

    public String path() {
        return path;
    }

    public String requestName() {
        return requestName;
    }
}
