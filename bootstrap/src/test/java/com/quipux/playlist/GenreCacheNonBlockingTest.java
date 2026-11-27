package com.quipux.playlist;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.blockhound.BlockHound;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenreCacheNonBlockingTest extends SpotifyApiTestSupport {

    private static final String CACHE_KEY = "playlist:genres:spotify";
    private static final GenericContainer<?> REDIS = startRedis();

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void configureRedisCache(DynamicPropertyRegistry registry) {
        registry.add("playlist.genres.cache.enabled", () -> "true");
        registry.add("playlist.genres.cache.ttl", () -> "10m");
        registry.add("playlist.genres.cache.timeout", () -> "1s");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.r2dbc.url", () -> "r2dbc:h2:mem:///genre-cache-nb?options=DB_CLOSE_DELAY=-1");
    }

    @BeforeAll
    void installBlockHoundAfterWarmingUp() {
        createClient();
        StepVerifier.create(everyCachedOutcome("warm-up"))
                .expectNextCount(1)
                .thenConsumeWhile(status -> true)
                .verifyComplete();
        BlockHound.install();
    }

    @AfterAll
    void stopRedis() {
        if (REDIS.isRunning()) {
            REDIS.stop();
        }
    }

    @Test
    void readsAndWritesRedisGenreCacheWithoutBlocking() {
        StepVerifier.create(everyCachedOutcome("non-blocking"))
                .expectNext("SPOTIFY", "SPOTIFY", "201", "LOCAL")
                .verifyComplete();
    }

    private Flux<String> everyCachedOutcome(String prefix) {
        return Flux.concat(
                redisTemplate.delete(CACHE_KEY).then(Mono.fromRunnable(() -> spotifyOffers("[\"rock\"]"))),
                genres(webClient).map(body -> String.valueOf(body.get("origen"))),
                genres(webClient).map(body -> String.valueOf(body.get("origen"))),
                create(playlist(prefix, Map.of("titulo", "Uno", "genero", "rock"))).map(String::valueOf),
                redisTemplate.delete(CACHE_KEY).then(Mono.fromRunnable(() -> spotifyFails(503))),
                genres(webClient).map(body -> String.valueOf(body.get("origen"))));
    }

    private static GenericContainer<?> startRedis() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:8.12.0-alpine"))
                .withExposedPorts(6379);
        container.start();
        return container;
    }
}
