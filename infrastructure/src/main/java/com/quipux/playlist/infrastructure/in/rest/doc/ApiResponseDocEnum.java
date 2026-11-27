package com.quipux.playlist.infrastructure.in.rest.doc;

import com.quipux.playlist.domain.model.enums.ResponseCodeEnum;
import org.springframework.http.HttpStatus;

public enum ApiResponseDocEnum {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Datos, cuerpo o dirección no válidos",
            ResponseCodeEnum.INVALID_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos",
            ResponseCodeEnum.INVALID_CREDENTIALS),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Falta el token, no es válido o está vencido",
            ResponseCodeEnum.AUTHENTICATION_REQUIRED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "El rol no permite la operación", ResponseCodeEnum.ACCESS_DENIED),
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "La lista no existe", ResponseCodeEnum.PLAYLIST_NOT_FOUND),
    PLAYLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, "Ya existe una lista con ese nombre",
            ResponseCodeEnum.PLAYLIST_ALREADY_EXISTS),
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "El cuerpo supera el tamaño máximo", null),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo no es JSON", null),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones de recomendaciones",
            ResponseCodeEnum.RECOMMENDATION_RATE_LIMITED),
    UNEXPECTED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado", ResponseCodeEnum.UNEXPECTED_ERROR);

    private static final String CODE_FORMAT = "%s (%s)";

    private final HttpStatus status;
    private final String text;
    private final ResponseCodeEnum responseCode;

    ApiResponseDocEnum(HttpStatus status, String text, ResponseCodeEnum responseCode) {
        this.status = status;
        this.text = text;
        this.responseCode = responseCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String description() {
        return responseCode == null ? text : CODE_FORMAT.formatted(text, responseCode.code());
    }
}
