package com.quipux.playlist.domain.model;

import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_ALBUM_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_ARTIST_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_GENRE_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_RELEASE_YEAR_MAX_LENGTH;
import static com.quipux.playlist.domain.model.constants.PlaylistConstraints.SONG_TITLE_MAX_LENGTH;

public record Song(String title, String artist, String album, String releaseYear, String genre) {

    public Song {
        FieldRules.requireMaxLength(title, SONG_TITLE_MAX_LENGTH, "song title");
        FieldRules.requireMaxLength(artist, SONG_ARTIST_MAX_LENGTH, "song artist");
        FieldRules.requireMaxLength(album, SONG_ALBUM_MAX_LENGTH, "song album");
        FieldRules.requireMaxLength(releaseYear, SONG_RELEASE_YEAR_MAX_LENGTH, "song release year");
        FieldRules.requireMaxLength(genre, SONG_GENRE_MAX_LENGTH, "song genre");
    }
}
