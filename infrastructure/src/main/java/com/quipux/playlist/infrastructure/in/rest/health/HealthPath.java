package com.quipux.playlist.infrastructure.in.rest.health;

import java.util.Arrays;

public enum HealthPath {

    LIVENESS("/actuator/health/liveness"),
    READINESS("/actuator/health/readiness");

    private final String pattern;

    HealthPath(String pattern) {
        this.pattern = pattern;
    }

    public String pattern() {
        return pattern;
    }

    public static String[] publicPatterns() {
        return Arrays.stream(values())
                .map(HealthPath::pattern)
                .toArray(String[]::new);
    }
}
