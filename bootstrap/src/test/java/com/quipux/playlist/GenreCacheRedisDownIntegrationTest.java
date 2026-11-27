package com.quipux.playlist;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.infrastructure.in.rest.health.HealthPath;
import com.quipux.playlist.infrastructure.out.genre.SpotifyEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GenreCacheRedisDownIntegrationTest extends SpotifyApiTestSupport {

    private static final String CACHE_KEY = "playlist:genres:spotify";
    private static final GenericContainer<?> REDIS = startRedis();

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void configureRedisCache(DynamicPropertyRegistry registry) {
        registry.add("playlist.genres.cache.enabled", () -> "true");
        registry.add("playlist.genres.cache.ttl", () -> "10m");
        registry.add("playlist.genres.cache.timeout", () -> "500ms");
        registry.add("spring.data.redis.timeout", () -> "500ms");
        registry.add("spring.data.redis.connect-timeout", () -> "500ms");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///genre-cache-down-it?options=DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopRedisIfRunning() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @Test
    @Order(1)
    void servesWithinTimeoutWhenRedisIsPausedAndResumesCachingWhenUnpaused() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            spotifyOffers("[\"rock\"]");

            Duration elapsed = StepVerifier.create(genres(webClient))
                    .expectNext(Map.of("generos", List.of("rock"), "origen", "SPOTIFY"))
                    .verifyComplete();

            assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        StepVerifier.create(redisTemplate.delete(CACHE_KEY))
                .expectNextMatches(count -> count >= 0)
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
    @Order(2)
    void keepsServingGenresPlaylistsAndHealthProbesWhenRedisIsStopped() {
        REDIS.stop();
        spotifyOffers("[\"rock\"]");

        Duration spotifyElapsed = StepVerifier.create(genres(webClient))
                .expectNext(Map.of("generos", List.of("rock"), "origen", "SPOTIFY"))
                .verifyComplete();
        assertThat(spotifyElapsed).isLessThan(Duration.ofSeconds(2));

        StepVerifier.create(create(playlist("redis-down", Map.of("titulo", "Uno", "genero", "rock"))))
                .expectNext(201)
                .verifyComplete();

        spotifyFails(503);

        Duration fallbackElapsed = StepVerifier.create(genres(webClient))
                .assertNext(body -> assertThat(body).containsEntry("origen", "LOCAL"))
                .verifyComplete();
        assertThat(fallbackElapsed).isLessThan(Duration.ofSeconds(5));

        for (String path : HealthPath.publicPatterns()) {
            StepVerifier.create(anonymousClient.get().uri(path).retrieve().bodyToMono(JsonNode.class))
                    .assertNext(body -> assertThat(body.path("status").asString()).isEqualTo("UP"))
                    .verifyComplete();
        }
    }

    private static GenericContainer<?> startRedis() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:8.12.0-alpine"))
                .withExposedPorts(6379);
        container.start();
        return container;
    }
}
