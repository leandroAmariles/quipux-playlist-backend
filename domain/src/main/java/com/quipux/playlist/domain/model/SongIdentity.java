package com.quipux.playlist.domain.model;

import java.util.Locale;

/**
 * What makes two songs the same one: title and artist, ignoring case and surrounding spaces.
 */
record SongIdentity(String title, String artist) {

    static SongIdentity of(String title, String artist) {
        return new SongIdentity(normalize(title), normalize(artist));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
