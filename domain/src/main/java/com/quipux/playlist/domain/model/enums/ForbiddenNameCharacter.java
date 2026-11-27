package com.quipux.playlist.domain.model.enums;

import java.util.Arrays;

public enum ForbiddenNameCharacter {

    SLASH('/'),
    BACKSLASH('\\');

    private final char value;

    ForbiddenNameCharacter(char value) {
        this.value = value;
    }

    public static boolean isForbidden(int character) {
        return Arrays.stream(values()).anyMatch(forbidden -> forbidden.value == character);
    }
}
