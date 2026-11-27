package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.FieldNameEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorMessageEnumTest {

    @ParameterizedTest
    @CsvSource({
            "INVALID_CREDENTIALS, AUTH_ERROR_001",
            "AUTHENTICATION_REQUIRED, AUTH_ERROR_002",
            "ACCESS_DENIED, AUTH_ERROR_003",
            "SIGNING_KEY_TOO_SHORT, AUTH_ERROR_004"})
    void tiesEachAuthenticationMessageToItsCode(ErrorMessageEnum message, String code) {
        assertThat(message.responseCode().code()).isEqualTo(code);
    }

    @Test
    void fillsTheValuesOfAMessage() {
        assertThat(ErrorMessageEnum.FIELD_REQUIRED.format(FieldNameEnum.PASSWORD.label()))
                .isEqualTo("The password is required");
        assertThat(ErrorMessageEnum.ACCESS_TOKEN_LIFETIME.withCode())
                .isEqualTo("AUTH_ERROR_004: An access token must last at least one second");
    }

    @Test
    void keepsThePercentSignsOfAMessageWithoutValues() {
        assertThat(ErrorMessageEnum.PLAYLIST_NAME_BLOCKED_SEQUENCE.format()).contains("'%2F'", "'%0D'");
    }
}
