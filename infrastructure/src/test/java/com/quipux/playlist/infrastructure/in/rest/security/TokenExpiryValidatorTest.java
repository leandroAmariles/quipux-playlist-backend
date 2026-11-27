package com.quipux.playlist.infrastructure.in.rest.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenExpiryValidatorTest {

    private static final Instant EXPIRY = Instant.parse("2026-01-01T10:00:00Z");

    @ParameterizedTest
    @CsvSource({"-1, false", "0, true", "1, true"})
    void rejectsFromTheInstantOfTheExpiry(long millisFromExpiry, boolean rejected) {
        TokenExpiryValidator validator = validatorAt(EXPIRY.plusMillis(millisFromExpiry));

        assertThat(validator.validate(token().expiresAt(EXPIRY).build()).hasErrors()).isEqualTo(rejected);
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        assertThat(validatorAt(EXPIRY).validate(token().build()).hasErrors()).isTrue();
    }

    private static TokenExpiryValidator validatorAt(Instant now) {
        return new TokenExpiryValidator(Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("admin");
    }
}
