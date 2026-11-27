package com.quipux.playlist.domain.model.enums;


public enum ResponseCodeEnum {

    INVALID_REQUEST("PLAYLIST_GE_00001"),
    PLAYLIST_NOT_FOUND("PLAYLIST_GE_00002"),
    PLAYLIST_ALREADY_EXISTS("PLAYLIST_GE_00003"),
    INVALID_CREDENTIALS("AUTH_ERROR_001"),
    AUTHENTICATION_REQUIRED("AUTH_ERROR_002"),
    ACCESS_DENIED("AUTH_ERROR_003"),
    INVALID_SECURITY_CONFIGURATION("AUTH_ERROR_004"),
    UNEXPECTED_ERROR("PLAYLIST_GE_99999");

    private final String code;

    ResponseCodeEnum(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
