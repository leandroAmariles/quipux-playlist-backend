package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

class PlaylistPersistenceFindByNameTest extends PersistenceTestSupport {

    @Test
    void returnsOnlyThatPlaylistWithItsSongsInOrder() {
        Playlist rock = new Playlist("Rock", "loud", List.of(song("zeta"), song("alpha")));
        Playlist jazz = new Playlist("Jazz", null, List.of(song("blue")));

        StepVerifier.create(adapter.save(rock).then(adapter.save(jazz)).then(adapter.findByName("Rock")))
                .expectNext(rock)
                .verifyComplete();
    }

    @Test
    void returnsAPlaylistWithoutSongs() {
        Playlist empty = new Playlist("Empty", null, List.of());

        StepVerifier.create(adapter.save(empty).then(adapter.findByName("Empty")))
                .expectNext(empty)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rock", "ROCK", " Rock", "Rock ", "Jazz", "R%", "' OR '1'='1"})
    void emitsNothingUnlessTheNameMatchesExactly(String name) {
        StepVerifier.create(adapter.save(new Playlist("Rock", null, List.of())).then(adapter.findByName(name)))
                .verifyComplete();
    }

    private static Song song(String title) {
        return new Song(title, null, null, null, null);
    }
}
