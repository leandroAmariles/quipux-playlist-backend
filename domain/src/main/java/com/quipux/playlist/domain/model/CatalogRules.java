package com.quipux.playlist.domain.model;

import com.quipux.playlist.domain.model.constants.CatalogConstraints;
import com.quipux.playlist.domain.model.constants.PlaylistConstraints;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a catalog must meet so that every recommendation can be added to a playlist.
 */
public final class CatalogRules {

    private CatalogRules() {
    }

    public static void requireValid(List<CatalogSong> songs, GenreCatalog allowedGenres) {
        if (songs == null || songs.size() < CatalogConstraints.MIN_SONGS
                || songs.size() > CatalogConstraints.MAX_SONGS) {
            throw invalid("it must hold from " + CatalogConstraints.MIN_SONGS + " to "
                    + CatalogConstraints.MAX_SONGS + " songs");
        }
        Set<SongIdentity> seen = new HashSet<>();
        for (int index = 0; index < songs.size(); index++) {
            requireValid(songs.get(index), index + 1, allowedGenres);
            if (!seen.add(SongIdentity.of(songs.get(index).title(), songs.get(index).artist()))) {
                throw invalid(index + 1, "its title and artist are repeated");
            }
        }
    }

    private static void requireValid(CatalogSong song, int position, GenreCatalog allowedGenres) {
        requireText(song.title(), PlaylistConstraints.SONG_TITLE_MAX_LENGTH, position, "title");
        requireText(song.artist(), PlaylistConstraints.SONG_ARTIST_MAX_LENGTH, position, "artist");
        requireText(song.album(), PlaylistConstraints.SONG_ALBUM_MAX_LENGTH, position, "album");
        if (song.releaseYear() == null || !CatalogConstraints.RELEASE_YEAR.matcher(song.releaseYear()).matches()) {
            throw invalid(position, "the year must have four digits");
        }
        requireText(song.genre(), PlaylistConstraints.SONG_GENRE_MAX_LENGTH, position, "genre");
        if (!allowedGenres.includesAll(List.of(song.genre()))) {
            throw invalid(position, "the genre is not in the local genre list");
        }
    }

    private static void requireText(String value, int maximum, int position, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw invalid(position, "the " + field + " must hold between 1 and " + maximum + " characters");
        }
    }

    private static IllegalStateException invalid(int position, String reason) {
        return invalid("song " + position + ": " + reason);
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException(ErrorMessageEnum.CATALOG_INVALID.withCode(reason));
    }
}
