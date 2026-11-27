package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;

final class FieldRules {

    private FieldRules() {
    }

    static void requireMaxLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new InvalidPlaylistException("The " + field + " must not exceed " + maximum + " characters");
        }
    }
}
