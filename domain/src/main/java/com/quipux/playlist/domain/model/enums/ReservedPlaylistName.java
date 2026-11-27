package com.quipux.playlist.domain.model.enums;

import java.util.Arrays;

public enum ReservedPlaylistName {

    CURRENT_DIRECTORY("."),
    PARENT_DIRECTORY("..");

    private final String value;

    ReservedPlaylistName(String value) {
        this.value = value;
    }

    public static boolean isReserved(String name) {
        return Arrays.stream(values()).anyMatch(reserved -> reserved.value.equals(name));
    }
}
