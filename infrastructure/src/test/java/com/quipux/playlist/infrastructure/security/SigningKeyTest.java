package com.quipux.playlist.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SigningKeyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"                                        ", "short-fake-key"})
    void rejectsAMissingOrShortSecretNamingTheVariable(String secret) {
        assertThatThrownBy(() -> new SigningKey(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejects31BytesWithoutShowingThem() {
        String secret = "k".repeat(31);

        assertThatThrownBy(() -> new SigningKey(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secret);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk", "ññññññññññññññññ"})
    void accepts32Bytes(String secret) {
        assertThat(new SigningKey(secret).secretKey().getEncoded()).hasSize(32);
    }
}
