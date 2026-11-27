package com.quipux.playlist.domain.model.enums;

import java.util.Arrays;
import java.util.Locale;

public enum ForbiddenNameSequence {

    ENCODED_SLASH("%2f"),
    ENCODED_BACKSLASH("%5c"),
    ENCODED_NULL("%00"),
    ENCODED_LINE_FEED("%0a"),
    ENCODED_CARRIAGE_RETURN("%0d"),
    LINE_SEPARATOR(" "),
    PARAGRAPH_SEPARATOR(" ");

    private final String value;

    ForbiddenNameSequence(String value) {
        this.value = value;
    }

    public static boolean isPresentIn(String name) {
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(forbidden -> lowerCaseName.contains(forbidden.value));
    }
}
