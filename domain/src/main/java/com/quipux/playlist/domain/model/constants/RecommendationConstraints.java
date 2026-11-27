package com.quipux.playlist.domain.model.constants;

public final class RecommendationConstraints {

    public static final int MAX_RECOMMENDATIONS = 5;
    public static final int MIN_DEFAULT_RECOMMENDATIONS = 5;
    // The limits of a song, so that a suggestion can always be added to a playlist.
    public static final int TITLE_MAX_LENGTH = PlaylistConstraints.SONG_TITLE_MAX_LENGTH;
    public static final int ARTIST_MAX_LENGTH = PlaylistConstraints.SONG_ARTIST_MAX_LENGTH;
    public static final int GENRE_MAX_LENGTH = PlaylistConstraints.SONG_GENRE_MAX_LENGTH;
    public static final int REASON_MAX_LENGTH = 120;

    private RecommendationConstraints() {
    }
}
