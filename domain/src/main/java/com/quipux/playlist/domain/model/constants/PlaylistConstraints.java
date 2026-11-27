package com.quipux.playlist.domain.model.constants;

public final class PlaylistConstraints {

    public static final int NAME_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 1000;
    public static final int SONG_TITLE_MAX_LENGTH = 255;
    public static final int SONG_ARTIST_MAX_LENGTH = 255;
    public static final int SONG_ALBUM_MAX_LENGTH = 255;
    public static final int SONG_RELEASE_YEAR_MAX_LENGTH = 10;
    public static final int SONG_GENRE_MAX_LENGTH = 100;

    private PlaylistConstraints() {
    }
}
