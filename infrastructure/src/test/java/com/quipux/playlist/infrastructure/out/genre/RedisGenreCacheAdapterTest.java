package com.quipux.playlist.infrastructure.out.genre;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.exception.GenreCacheUnavailableException;
import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RedisGenreCacheAdapterTest {

    private static final String KEY = "playlist:genres:spotify";
    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration TIMEOUT = Duration.ofMillis(200);

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RedisGenreCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisGenreCacheAdapter(
                redisTemplate, new ObjectMapper(), new GenreCacheSettings(KEY, TTL, TIMEOUT));
    }

    @Test
    void readsStoredJsonGenres() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(Mono.just("[\"rock\",\"pop\"]"));

        StepVerifier.create(adapter.find())
                .expectNext(List.of("rock", "pop"))
                .verifyComplete();
    }

    @Test
    void completesEmptyWhenKeyDoesNotExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.find())
                .verifyComplete();
    }

    @Test
    void failsAsUnreadableWhenJsonIsCorruptOrEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(Mono.just("{broken"), Mono.just("[]"), Mono.just("   "));

        for (int i = 0; i < 3; i++) {
            StepVerifier.create(adapter.find())
                    .expectErrorSatisfies(error -> {
                        assertThat(error)
                                .isInstanceOf(GenreCacheUnavailableException.class)
                                .hasMessageContaining("unreadable content");
                        assertThat(((GenreCacheUnavailableException) error).responseCode())
                                .isEqualTo(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE);
                    })
                    .verify();
        }
    }

    @Test
    void translatesReadErrorWithoutLeakingRawCause() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(Mono.error(new IllegalStateException("secret-redis-password")));

        StepVerifier.create(adapter.find())
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(GenreCacheUnavailableException.class)
                            .hasMessageContaining("unavailable during read")
                            .hasMessageNotContaining("secret-redis-password");
                    assertThat(((GenreCacheUnavailableException) error).responseCode())
                            .isEqualTo(ResponseCodeEnum.GENRE_CACHE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    void translatesReadTimeout() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(KEY)).thenReturn(Mono.never());

        StepVerifier.create(adapter.find())
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(GenreCacheUnavailableException.class)
                        .hasMessageContaining("timed out during read"))
                .verify();
    }

    @Test
    void savesSerializedGenresWithConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(KEY, "[\"rock\",\"pop\"]", TTL)).thenReturn(Mono.just(true));

        StepVerifier.create(adapter.save(List.of("rock", "pop")))
                .verifyComplete();
        verify(valueOperations).set(KEY, "[\"rock\",\"pop\"]", TTL);
    }

    @Test
    void translatesWriteTimeoutAndError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(KEY, "[\"rock\"]", TTL))
                .thenReturn(Mono.never())
                .thenReturn(Mono.error(new IllegalStateException("secret-redis-password")));

        StepVerifier.create(adapter.save(List.of("rock")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(GenreCacheUnavailableException.class)
                        .hasMessageContaining("timed out during write"))
                .verify();
        StepVerifier.create(adapter.save(List.of("rock")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(GenreCacheUnavailableException.class)
                        .hasMessageNotContaining("secret-redis-password"))
                .verify();
    }

    @Test
    void noOpAdapterAlwaysCompletesEmpty() {
        NoOpGenreCacheAdapter noOp = new NoOpGenreCacheAdapter();

        StepVerifier.create(noOp.find()).verifyComplete();
        StepVerifier.create(noOp.save(List.of("rock"))).verifyComplete();
    }

    @Test
    void settingsRejectInvalidKeyTtlOrTimeout() {
        assertThatThrownBy(() -> new GenreCacheSettings(" ", TTL, TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENRE_ERROR_002");
        assertThatThrownBy(() -> new GenreCacheSettings(KEY, Duration.ZERO, TIMEOUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENRE_ERROR_002");
        assertThatThrownBy(() -> new GenreCacheSettings(KEY, TTL, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENRE_ERROR_002");
    }
}
