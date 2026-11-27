package com.quipux.playlist.infrastructure.out.genre;

import com.quipux.playlist.application.port.out.GenreCachePort;
import com.quipux.playlist.domain.exception.GenreCacheUnavailableException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class RedisGenreCacheAdapter implements GenreCachePort {

    private static final String READ_OPERATION = "read";
    private static final String WRITE_OPERATION = "write";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final GenreCacheSettings settings;

    public RedisGenreCacheAdapter(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            GenreCacheSettings settings) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    @Override
    public Mono<List<String>> find() {
        return Mono.defer(() -> redisTemplate.opsForValue().get(settings.key()))
                .timeout(settings.timeout())
                .onErrorMap(TimeoutException.class, error ->
                        new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_TIMEOUT, READ_OPERATION))
                .onErrorMap(error -> !(error instanceof GenreCacheUnavailableException), error ->
                        new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNAVAILABLE, READ_OPERATION))
                .flatMap(this::deserialize);
    }

    @Override
    public Mono<Void> save(List<String> genres) {
        return Mono.defer(() -> redisTemplate.opsForValue().set(settings.key(), serialize(genres), settings.ttl()))
                .timeout(settings.timeout())
                .onErrorMap(TimeoutException.class, error ->
                        new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_TIMEOUT, WRITE_OPERATION))
                .onErrorMap(error -> !(error instanceof GenreCacheUnavailableException), error ->
                        new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNAVAILABLE, WRITE_OPERATION))
                .then();
    }

    private Mono<List<String>> deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mono.error(new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNREADABLE));
        }
        try {
            String[] values = objectMapper.readValue(raw, String[].class);
            if (values == null) {
                return Mono.error(new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNREADABLE));
            }
            List<String> genres = Arrays.stream(values)
                    .filter(Objects::nonNull)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (genres.isEmpty()) {
                return Mono.error(new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNREADABLE));
            }
            return Mono.just(genres);
        } catch (JacksonException exception) {
            return Mono.error(new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNREADABLE));
        }
    }

    private String serialize(List<String> genres) {
        try {
            return objectMapper.writeValueAsString(genres);
        } catch (JacksonException exception) {
            throw new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNREADABLE);
        }
    }
}
