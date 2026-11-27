package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_ALBUM_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_ARTIST_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_GENRE_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_RELEASE_YEAR_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_TITLE_MAX_LENGTH;

import com.quipux.playlist.domain.model.enums.FieldNameEnum;

public record Song(String title, String artist, String album, String releaseYear, String genre) {

    public Song {
        FieldRules.requireMaxLength(title, SONG_TITLE_MAX_LENGTH, FieldNameEnum.SONG_TITLE);
        FieldRules.requireMaxLength(artist, SONG_ARTIST_MAX_LENGTH, FieldNameEnum.SONG_ARTIST);
        FieldRules.requireMaxLength(album, SONG_ALBUM_MAX_LENGTH, FieldNameEnum.SONG_ALBUM);
        FieldRules.requireMaxLength(releaseYear, SONG_RELEASE_YEAR_MAX_LENGTH, FieldNameEnum.SONG_RELEASE_YEAR);
        FieldRules.requireMaxLength(genre, SONG_GENRE_MAX_LENGTH, FieldNameEnum.SONG_GENRE);
    }
}
