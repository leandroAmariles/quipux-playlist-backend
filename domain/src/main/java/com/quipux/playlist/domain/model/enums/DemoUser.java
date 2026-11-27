package com.quipux.playlist.domain.model.enums;

public enum DemoUser {

    USER("user", Role.USER),
    ADMIN("admin", Role.ADMIN);

    private final String username;
    private final Role role;

    DemoUser(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    public String username() {
        return username;
    }

    public Role role() {
        return role;
    }
}
