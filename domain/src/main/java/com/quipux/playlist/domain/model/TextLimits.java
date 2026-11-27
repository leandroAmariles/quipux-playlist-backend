package com.quipux.playlist.domain.model;

final class TextLimits {

    private TextLimits() {
    }

    /**
     * At most {@code maximum} characters, counted as code points so that none is cut in half.
     */
    static String trim(String value, int maximum) {
        if (value == null || value.codePointCount(0, value.length()) <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }
}
