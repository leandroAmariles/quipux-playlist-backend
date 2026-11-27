package com.quipux.playlist.domain.model.enums;

import java.util.Optional;

public enum PlaylistEventType {

    PLAYLIST_CREATED("PlaylistCreated"),
    PLAYLIST_DELETED("PlaylistDeleted");

    private final String externalName;

    PlaylistEventType(String externalName) {
        this.externalName = externalName;
    }

    public String externalName() {
        return externalName;
    }

    public static Optional<PlaylistEventType> fromExternalName(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (PlaylistEventType type : values()) {
            if (type.externalName.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
