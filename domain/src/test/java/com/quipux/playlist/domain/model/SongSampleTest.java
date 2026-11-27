package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SongSampleTest {

    private static final String NOTE = "🎵";

    private final SongSample sample = new SongSample(50, 100);

    @Test
    void takesTheFirstSongs() {
        List<Song> songs = IntStream.range(0, 60)
                .mapToObj(index -> new Song("Song " + index, "Artist", null, null, null))
                .toList();

        assertThat(sample.of(songs)).containsExactlyElementsOf(songs.subList(0, 50));
    }

    @Test
    void trimsEveryField() {
        Song song = new Song("t".repeat(200), "a".repeat(200), "b".repeat(200), "1999", "g".repeat(100));

        assertThat(new SongSample(50, 3).of(List.of(song)))
                .containsExactly(new Song("ttt", "aaa", "bbb", "199", "ggg"));
    }

    @Test
    void doesNotSplitACharacterWhenTrimming() {
        Song song = new Song("ab" + NOTE + NOTE, null, null, null, null);

        assertThat(new SongSample(50, 3).of(List.of(song)))
                .containsExactly(new Song("ab" + NOTE, null, null, null, null));
    }

    @Test
    void keepsMissingAndShortFields() {
        Song song = new Song("Title", null, "", null, "rock");

        assertThat(sample.of(List.of(song))).containsExactly(song);
    }
}
