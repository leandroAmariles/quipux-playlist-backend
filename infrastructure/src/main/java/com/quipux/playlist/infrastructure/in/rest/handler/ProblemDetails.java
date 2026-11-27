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
            case INVALID_SECURITY_CONFIGURATION, UNEXPECTED_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
