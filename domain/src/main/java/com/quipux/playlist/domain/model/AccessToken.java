package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.FieldNameEnum;
import java.util.Objects;

public record AccessToken(String value, long expiresInSeconds) {

    public AccessToken {
        Objects.requireNonNull(value, FieldNameEnum.ACCESS_TOKEN.label());
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException(ErrorMessageEnum.ACCESS_TOKEN_LIFETIME.withCode());
        }
    }

    @Override
    public String toString() {
        return "AccessToken[expiresInSeconds=" + expiresInSeconds + "]";
    }
}
