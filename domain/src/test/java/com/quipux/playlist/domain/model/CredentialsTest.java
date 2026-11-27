package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quipux.playlist.domain.exception.InvalidLoginRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CredentialsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankUsername(String username) {
        assertThatThrownBy(() -> new Credentials(username, "password"))
                .isInstanceOf(InvalidLoginRequestException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingOrBlankPassword(String password) {
        assertThatThrownBy(() -> new Credentials("user", password))
                .isInstanceOf(InvalidLoginRequestException.class);
    }

    @Test
    void keepsValuesExactlyAsSent() {
        Credentials credentials = new Credentials(" User ", " Secret ");

        assertThat(credentials.username()).isEqualTo(" User ");
        assertThat(credentials.password()).isEqualTo(" Secret ");
    }

    @ParameterizedTest
    @CsvSource({"p, 72, false", "p, 73, true", "ñ, 36, false", "ñ, 37, true"})
    void measuresThePasswordLimitInBytes(String character, int times, boolean exceeds) {
        assertThat(new Credentials("user", character.repeat(times)).passwordExceedsLimit()).isEqualTo(exceeds);
    }

    @Test
    void doesNotShowThePassword() {
        assertThat(new Credentials("user", "top-secret").toString()).contains("user").doesNotContain("top-secret");
    }
}
