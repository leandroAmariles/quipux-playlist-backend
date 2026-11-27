package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class PlaylistPersistenceFindAllTest extends PersistenceTestSupport {

    @Test
    void emitsNothingWithoutPlaylists() {
        StepVerifier.create(adapter.findAll()).verifyComplete();
    }

    @Test
    void returnsPlaylistsInCreationOrderEachWithItsOwnSongs() {
        Playlist rock = new Playlist("Rock", "loud",
                List.of(new Song("zeta", "artist", "album", "1999", "rock"), song("alpha")));
        Playlist empty = new Playlist("Empty", null, List.of());
        Playlist jazz = new Playlist("Jazz", null, List.of(song("blue"), song("blue")));

        StepVerifier.create(adapter.save(rock).then(adapter.save(empty)).then(adapter.save(jazz)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(adapter.findAll())
                .expectNext(rock, empty, jazz)
                .verifyComplete();
    }

    private static Song song(String title) {
        return new Song(title, null, null, null, null);
    }
}
