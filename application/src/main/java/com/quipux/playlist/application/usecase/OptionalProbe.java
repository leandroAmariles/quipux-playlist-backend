package com.quipux.playlist.application.usecase;

import java.util.Optional;

public final class OptionalProbe {

    private OptionalProbe() {
    }

    public static String read(Optional<String> value) {
        return value.orElse("");
    }
}
