package com.quipux.playlist.infrastructure.in.rest.security;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class TokenExpiryValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error EXPIRED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, ErrorMessageEnum.TOKEN_EXPIRED.format(), null);

    private final Clock clock;

    public TokenExpiryValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant expiresAt = token.getExpiresAt();
        if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
            return OAuth2TokenValidatorResult.failure(EXPIRED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
