package com.quipux.playlist.infrastructure.in.rest.handler;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

    private static final String CODE_PROPERTY = "code";

    private ProblemDetails() {
    }


    public static ProblemDetail of(ResponseCodeEnum responseCode, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(statusOf(responseCode), message);
        addCode(detail, responseCode);
        return detail;
    }

    static void addCode(ProblemDetail detail, ResponseCodeEnum responseCode) {
        detail.setProperty(CODE_PROPERTY, responseCode.code());
    }

    private static HttpStatus statusOf(ResponseCodeEnum responseCode) {
        return switch (responseCode) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case PLAYLIST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PLAYLIST_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case RECOMMENDATION_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case GENRE_PROVIDER_UNAVAILABLE, RECOMMENDATION_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_SECURITY_CONFIGURATION, INVALID_GENRE_CONFIGURATION,
                    INVALID_RECOMMENDATION_CONFIGURATION, UNEXPECTED_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
