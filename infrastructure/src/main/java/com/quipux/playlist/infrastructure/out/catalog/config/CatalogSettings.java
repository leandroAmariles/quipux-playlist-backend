package com.quipux.playlist.infrastructure.out.catalog.config;

import java.time.Duration;

/**
 * How to reach the database of the catalog.
 */
public record CatalogSettings(
        String jdbcUrl,
        String username,
        String password,
        int poolSize,
        Duration connectionTimeout,
        Duration socketTimeout,
        Duration queryTimeout) {

    boolean isConfigured() {
        return !jdbcUrl.isBlank() && !password.isBlank();
    }

    // The password is never written anywhere.
    @Override
    public String toString() {
        return "CatalogSettings[username=" + username + ", poolSize=" + poolSize + "]";
    }
}
