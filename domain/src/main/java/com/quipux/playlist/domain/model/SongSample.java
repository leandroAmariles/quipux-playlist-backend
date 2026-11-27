package com.quipux.playlist.domain.model;

import java.util.List;

/**
 * How much of a playlist is sent to the model: its first songs, with every field trimmed.
 */
public record SongSample(int maxSongs, int maxFieldLength) {

    public List<Song> of(List<Song> songs) {
        return songs.stream()
                .limit(maxSongs)
                .map(this::trimmed)
                .toList();
    }

    private Song trimmed(Song song) {
        return new Song(
                TextLimits.trim(song.title(), maxFieldLength),
                TextLimits.trim(song.artist(), maxFieldLength),
                TextLimits.trim(song.album(), maxFieldLength),
                TextLimits.trim(song.releaseYear(), maxFieldLength),
                TextLimits.trim(song.genre(), maxFieldLength));
    }
}
