package com.quipux.playlist.infrastructure.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("APP_USER")
public record UserEntity(@Id Long id, String username, String passwordHash, String role) {

    @Override
    public String toString() {
        return "UserEntity[id=" + id + ", username=" + username + ", role=" + role + "]";
    }
}
