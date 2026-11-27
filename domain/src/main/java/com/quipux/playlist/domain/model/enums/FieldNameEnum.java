package com.quipux.playlist.domain.model.enums;

public enum FieldNameEnum {

    PLAYLIST_NAME("playlist name"),
    PLAYLIST_DESCRIPTION("playlist description"),
    SONG_TITLE("song title"),
    SONG_ARTIST("song artist"),
    SONG_ALBUM("song album"),
    SONG_RELEASE_YEAR("song release year"),
    SONG_GENRE("song genre"),
    USERNAME("username"),
    PASSWORD("password"),
    PASSWORD_HASH("password hash"),
    ROLE("role"),
    ACCESS_TOKEN("access token");

    private final String label;

    FieldNameEnum(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
