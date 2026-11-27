package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.quipux.playlist.infrastructure.out.genre.SpotifyEndpoint;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

abstract class SpotifyApiTestSupport extends ApiTestSupport {

    protected static final String CLIENT_SECRET = "fake-spotify-secret";
    protected static final String SPOTIFY_TOKEN = "fake-spotify-token";
    protected static final WireMockServer SPOTIFY = startSpotify();

    @DynamicPropertySource
    static void pointToTheSimulatedSpotify(DynamicPropertyRegistry registry) {
        registry.add("playlist.spotify.accounts-base-url", SPOTIFY::baseUrl);
        registry.add("playlist.spotify.api-base-url", SPOTIFY::baseUrl);
        registry.add("playlist.spotify.client-id", () -> "fake-spotify-id");
        registry.add("playlist.spotify.client-secret", () -> CLIENT_SECRET);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///spotify-test?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetSpotify() {
        SPOTIFY.resetAll();
    }

    protected static void spotifyOffers(String genresJson) {
        SPOTIFY.stubFor(post(SpotifyEndpoint.TOKEN.path()).willReturn(
                okJson("{\"access_token\":\"" + SPOTIFY_TOKEN + "\",\"token_type\":\"bearer\"}")));
        SPOTIFY.stubFor(get(SpotifyEndpoint.GENRE_SEEDS.path()).willReturn(
                okJson("{\"genres\":" + genresJson + "}")));
    }

    protected static void spotifyFails(int code) {
        spotifyOffers("[]");
        SPOTIFY.stubFor(get(SpotifyEndpoint.GENRE_SEEDS.path()).willReturn(status(code)));
    }

    protected static int spotifyRequests() {
        return SPOTIFY.getAllServeEvents().size();
    }

    protected static Mono<Map<String, Object>> genres(WebClient client) {
        return client.get().uri("/genres")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private static WireMockServer startSpotify() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }
}
