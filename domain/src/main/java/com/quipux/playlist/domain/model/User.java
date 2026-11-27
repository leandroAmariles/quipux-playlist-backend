package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.enums.FieldNameEnum;
import com.quipux.playlist.domain.model.enums.Role;
import java.util.Objects;

public record User(String username, String passwordHash, Role role) {

    public User {
        Objects.requireNonNull(username, FieldNameEnum.USERNAME.label());
        Objects.requireNonNull(passwordHash, FieldNameEnum.PASSWORD_HASH.label());
        Objects.requireNonNull(role, FieldNameEnum.ROLE.label());
    }

    @Override
    public String toString() {
        return "User[username=" + username + ", role=" + role + "]";
    }
}
