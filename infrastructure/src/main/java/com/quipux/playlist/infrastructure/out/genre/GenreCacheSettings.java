package com.quipux.playlist.infrastructure.out.genre;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;

public record GenreCacheSettings(String key, Duration ttl, Duration timeout) {

    public GenreCacheSettings {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_GENRE_CACHE_KEY.withCode());
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_GENRE_CACHE_DURATION.withCode("ttl"));
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(ErrorMessageEnum.INVALID_GENRE_CACHE_DURATION.withCode("timeout"));
        }
    }
}
