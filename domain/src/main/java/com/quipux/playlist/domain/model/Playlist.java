package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.DESCRIPTION_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.NAME_MAX_LENGTH;

import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.FieldNameEnum;
import com.quipux.playlist.domain.model.enums.ForbiddenNameCharacter;
import com.quipux.playlist.domain.model.enums.ForbiddenNameSequence;
import com.quipux.playlist.domain.model.enums.ReservedPlaylistName;
import java.util.List;
import java.util.Objects;

public record Playlist(String name, String description, List<Song> songs) {

    public Playlist {
        requireValidName(name);
        FieldRules.requireMaxLength(description, DESCRIPTION_MAX_LENGTH, FieldNameEnum.PLAYLIST_DESCRIPTION);
        songs = copyOf(songs);
    }

    public List<String> declaredGenres() {
        return songs.stream()
                .map(Song::genre)
                .filter(genre -> genre != null && !genre.isBlank())
                .toList();
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidPlaylistException(ErrorMessageEnum.PLAYLIST_NAME_REQUIRED);
        }
        FieldRules.requireMaxLength(name, NAME_MAX_LENGTH, FieldNameEnum.PLAYLIST_NAME);
        if (ReservedPlaylistName.isReserved(name) || name.chars().anyMatch(Playlist::isForbiddenInName)) {
            throw new InvalidPlaylistException(ErrorMessageEnum.PLAYLIST_NAME_NOT_ADDRESSABLE);
        }
        if (ForbiddenNameSequence.isPresentIn(name)) {
            throw new InvalidPlaylistException(ErrorMessageEnum.PLAYLIST_NAME_BLOCKED_SEQUENCE);
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
            throw new InvalidPlaylistException(ErrorMessageEnum.NULL_SONG);
        }
        return List.copyOf(songs);
    }
}
