package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PasswordConstraints.MAX_BYTES;

import com.quipux.playlist.domain.exception.InvalidLoginRequestException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.FieldNameEnum;
import java.nio.charset.StandardCharsets;

public record Credentials(String username, String password) {

    public Credentials {
        requireText(username, FieldNameEnum.USERNAME);
        requireText(password, FieldNameEnum.PASSWORD);
    }

    public boolean passwordExceedsLimit() {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    @Override
    public String toString() {
        return "Credentials[username=" + username + "]";
    }

    private static void requireText(String value, FieldNameEnum field) {
        if (value == null || value.isBlank()) {
            throw new InvalidLoginRequestException(ErrorMessageEnum.FIELD_REQUIRED, field.label());
        }
    }
}
