package com.quipux.playlist.infrastructure.out.genre.config;

import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.infrastructure.out.genre.LocalGenreProviderAdapter;
import com.quipux.playlist.infrastructure.out.genre.SpotifyGenreProviderAdapter;
import com.quipux.playlist.infrastructure.out.genre.SpotifySettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GenreProviderConfig {

    @Value("${playlist.spotify.accounts-base-url:https://accounts.spotify.com}")
    private String accountsBaseUrl;

    @Value("${playlist.spotify.api-base-url:https://api.spotify.com}")
    private String apiBaseUrl;

    @Value("${playlist.spotify.client-id:}")
    private String clientId;

    @Value("${playlist.spotify.client-secret:}")
    private String clientSecret;

    @Value("${playlist.spotify.timeout:3s}")
    private Duration timeout;

    @Value("${playlist.genres.local-file:classpath:local-genres.properties}")
    private Resource localGenres;

    @Bean
    GenreProviderPort spotifyGenreProvider() {
        return new SpotifyGenreProviderAdapter(WebClient.create(),
                new SpotifySettings(accountsBaseUrl, apiBaseUrl, clientId, clientSecret, timeout));
    }

    @Bean
    GenreProviderPort localGenreProvider() {
        return new LocalGenreProviderAdapter(localGenres);
    }
}
