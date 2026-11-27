package com.quipux.playlist.infrastructure.out.genre;

import java.time.Duration;

public record SpotifySettings(
        String accountsBaseUrl, String apiBaseUrl, String clientId, String clientSecret, Duration timeout) {

    public boolean hasCredentials() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public String toString() {
        return "SpotifySettings[accountsBaseUrl=" + accountsBaseUrl + ", apiBaseUrl=" + apiBaseUrl
                + ", timeout=" + timeout + "]";
    }
}
