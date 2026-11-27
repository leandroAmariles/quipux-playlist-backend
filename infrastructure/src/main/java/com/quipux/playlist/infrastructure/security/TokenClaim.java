package com.quipux.playlist.infrastructure.security;

public enum TokenClaim {

    ROLE("role");

    private final String claimName;

    TokenClaim(String claimName) {
        this.claimName = claimName;
    }

    public String claimName() {
        return claimName;
    }
}
