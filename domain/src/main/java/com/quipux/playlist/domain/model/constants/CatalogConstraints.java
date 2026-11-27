package com.quipux.playlist.domain.model.constants;

import java.util.regex.Pattern;

public final class CatalogConstraints {

    public static final int MIN_SONGS = 200;
    public static final int MAX_SONGS = 500;
    public static final Pattern RELEASE_YEAR = Pattern.compile("\\d{4}");

    private CatalogConstraints() {
    }
}
