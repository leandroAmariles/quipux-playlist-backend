package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.infrastructure.out.genre.SpotifyEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Testcontainers
class GenreCacheRedisIntegrationTest extends SpotifyApiTestSupport {

    private static final String CACHE_KEY = "playlist:genres:spotify";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.12.0-alpine"))
                    .withExposedPorts(6379);

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void configureRedisCache(DynamicPropertyRegistry registry) {
        registry.add("playlist.genres.cache.enabled", () -> "true");
        registry.add("playlist.genres.cache.ttl", () -> "2s");
        registry.add("playlist.genres.cache.timeout", () -> "1s");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///genre-cache-it?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void clearCache() {
        StepVerifier.create(redisTemplate.delete(CACHE_KEY))
                .expectNextMatches(count -> count >= 0)
                .verifyComplete();
    }

    @Test
    void reusesCachedGenresAcrossTenReadsAndPlaylistCreationWithinTtl() {
        spotifyOffers("[\"rock\",\"pop\"]");

        StepVerifier.create(Flux.range(0, 10).concatMap(ignored -> genres(webClient)).collectList())
                .assertNext(responses -> {
                    assertThat(responses).hasSize(10);
                    assertThat(responses).allSatisfy(body -> assertThat(body)
                            .containsEntry("origen", "SPOTIFY")
                            .containsEntry("generos", List.of("rock", "pop")));
                })
                .verifyComplete();

        StepVerifier.create(create(playlist("cached-rock", Map.of("titulo", "Uno", "genero", "Rock"))))
                .expectNext(201)
                .verifyComplete();

        SPOTIFY.verify(1, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path())));
    }

    @Test
    void callsSpotifyAgainAfterTtlExpires() {
        spotifyOffers("[\"rock\"]");

        StepVerifier.create(genres(webClient))
                .expectNext(Map.of("generos", List.of("rock"), "origen", "SPOTIFY"))
                .verifyComplete();

        StepVerifier.create(Mono.delay(Duration.ofMillis(2300)))
                .expectNext(0L)
                .verifyComplete();

        spotifyOffers("[\"jazz\"]");

        StepVerifier.create(genres(webClient))
                .expectNext(Map.of("generos", List.of("jazz"), "origen", "SPOTIFY"))
                .verifyComplete();

        SPOTIFY.verify(2, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path())));
    }

    @Test
    void doesNotCacheLocalFallbackAndRecoversImmediatelyWhenSpotifyReturns() {
        spotifyFails(503);

        StepVerifier.create(genres(webClient))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL"))
                .verifyComplete();

        StepVerifier.create(redisTemplate.hasKey(CACHE_KEY))
                .expectNext(false)
                .verifyComplete();

        spotifyOffers("[\"jazz\"]");

        StepVerifier.create(genres(webClient).concatWith(genres(webClient)))
                .expectNext(
                        Map.of("generos", List.of("jazz"), "origen", "SPOTIFY"),
                        Map.of("generos", List.of("jazz"), "origen", "SPOTIFY"))
                .verifyComplete();

        SPOTIFY.verify(2, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path())));
    }

    @Test
    void overwritesCorruptCachedEntryWhenSpotifyResponds() {
        StepVerifier.create(redisTemplate.opsForValue().set(CACHE_KEY, "{broken"))
                .expectNext(true)
                .verifyComplete();

        spotifyOffers("[\"indie\"]");

        StepVerifier.create(genres(webClient).concatWith(genres(webClient)))
                .expectNext(
                        Map.of("generos", List.of("indie"), "origen", "SPOTIFY"),
                        Map.of("generos", List.of("indie"), "origen", "SPOTIFY"))
                .verifyComplete();

        SPOTIFY.verify(1, getRequestedFor(urlEqualTo(SpotifyEndpoint.GENRE_SEEDS.path())));
    }
}
