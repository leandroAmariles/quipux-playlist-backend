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

    @ParameterizedTest
    @CsvSource({
            "RECOMMENDATION_RATE_LIMIT, RECOMMENDATION_ERROR_001",
            "RECOMMENDATION_PROVIDER_DISABLED, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_NOT_CONFIGURED, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_TIMEOUT, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_ERROR_RESPONSE, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_UNREACHABLE, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_EMPTY, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_UNREADABLE, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_CIRCUIT_OPEN, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_PROVIDER_TOO_MANY_CALLS, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_NO_VALID_SUGGESTIONS, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_EMPTY_PLAYLIST, RECOMMENDATION_ERROR_002",
            "RECOMMENDATION_FALLBACK, RECOMMENDATION_ERROR_002",
            "INVALID_REQUEST_QUOTA, RECOMMENDATION_ERROR_003",
            "DEFAULT_RECOMMENDATIONS_INVALID, RECOMMENDATION_ERROR_003",
            "PROMPT_UNAVAILABLE, RECOMMENDATION_ERROR_003",
            "CATALOG_INVALID, RECOMMENDATION_ERROR_003",
            "CATALOG_DISABLED, RECOMMENDATION_ERROR_004",
            "CATALOG_NOT_CONFIGURED, RECOMMENDATION_ERROR_004",
            "CATALOG_TIMEOUT, RECOMMENDATION_ERROR_004",
            "CATALOG_EMBEDDINGS_UNAVAILABLE, RECOMMENDATION_ERROR_004",
            "CATALOG_UNREACHABLE, RECOMMENDATION_ERROR_004",
            "CATALOG_INDEXING_FAILED, RECOMMENDATION_ERROR_004",
            "RECOMMENDATION_NO_CANDIDATES, RECOMMENDATION_ERROR_004",
            "RECOMMENDATION_SIMILARITY, RECOMMENDATION_ERROR_002"})
    void tiesEachRecommendationMessageToItsCode(ErrorMessageEnum message, String code) {
        assertThat(message.responseCode().code()).isEqualTo(code);
    }

    @Test
    void tiesEventMessagesToTheirCodes() {
        assertThat(ErrorMessageEnum.EVENT_PUBLISH_UNAVAILABLE.responseCode().code()).isEqualTo("EVENT_ERROR_001");
        assertThat(ErrorMessageEnum.EVENT_PUBLISH_TIMEOUT.responseCode().code()).isEqualTo("EVENT_ERROR_001");
        assertThat(ErrorMessageEnum.EVENT_SERIALIZATION_FAILED.responseCode().code()).isEqualTo("EVENT_ERROR_001");
        assertThat(ErrorMessageEnum.EVENT_PUBLISH_IGNORED.responseCode().code()).isEqualTo("EVENT_ERROR_001");
        assertThat(ErrorMessageEnum.EVENT_MESSAGE_UNREADABLE.responseCode().code()).isEqualTo("EVENT_ERROR_001");
        assertThat(ErrorMessageEnum.INVALID_EVENT_DURATION.responseCode().code()).isEqualTo("EVENT_ERROR_002");
        assertThat(ErrorMessageEnum.INVALID_EVENT_SETTING.responseCode().code()).isEqualTo("EVENT_ERROR_002");
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
