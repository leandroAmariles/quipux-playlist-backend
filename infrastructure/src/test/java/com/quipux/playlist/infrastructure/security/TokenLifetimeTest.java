package com.quipux.playlist.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenLifetimeTest {

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1M", "PT0.5S"})
    void rejectsALifetimeThatIsNotPositiveNamingTheVariable(String lifetime) {
        assertThatThrownBy(() -> new TokenLifetime(Duration.parse(lifetime)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_ERROR_004")
                .hasMessageContaining("JWT_TTL");
    }

    @Test
    void acceptsOneSecond() {
        assertThat(new TokenLifetime(Duration.ofSeconds(1)).duration()).hasSeconds(1);
    }
}
