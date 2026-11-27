package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.DESCRIPTION_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.NAME_MAX_LENGTH;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.enums.ForbiddenNameCharacter;
import com.quipux.playlist.domain.model.enums.ReservedPlaylistName;
import java.util.List;
import java.util.Objects;

public record Playlist(String name, String description, List<Song> songs) {

    public Playlist {
        requireValidName(name);
        FieldRules.requireMaxLength(description, DESCRIPTION_MAX_LENGTH, "playlist description");
        songs = copyOf(songs);
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidPlaylistException("The playlist name is required");
        }
        FieldRules.requireMaxLength(name, NAME_MAX_LENGTH, "playlist name");
        if (ReservedPlaylistName.isReserved(name) || name.chars().anyMatch(Playlist::isForbiddenInName)) {
            throw new InvalidPlaylistException(
                    "The playlist name must not be '.' or '..', nor contain '/', '\\' or control characters");
        }
    }

    private static boolean isForbiddenInName(int character) {
        return ForbiddenNameCharacter.isForbidden(character) || Character.isISOControl(character);
    }

    private static List<Song> copyOf(List<Song> songs) {
        if (songs == null) {
            return List.of();
        }
        if (songs.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPlaylistException("A song must not be null");
        }
        return List.copyOf(songs);
    }
}
