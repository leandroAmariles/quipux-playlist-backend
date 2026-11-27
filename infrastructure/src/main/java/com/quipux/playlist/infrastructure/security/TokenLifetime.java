package com.quipux.playlist.infrastructure.security;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;

public record TokenLifetime(Duration duration) {

    private static final String ENVIRONMENT_VARIABLE = "JWT_TTL";

    public TokenLifetime {
        if (duration.toSeconds() <= 0) {
            throw new IllegalStateException(
                    ErrorMessageEnum.CONFIGURED_LIFETIME_TOO_SHORT.withCode(ENVIRONMENT_VARIABLE));
        }
    }
}
