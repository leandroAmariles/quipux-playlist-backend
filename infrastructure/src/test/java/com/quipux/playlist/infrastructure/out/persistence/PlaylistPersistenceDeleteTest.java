package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class PlaylistPersistenceDeleteTest extends PersistenceTestSupport {

    private static final Playlist ROCK = new Playlist("Rock", null, List.of(song("first"), song("second")));
    private static final Playlist JAZZ = new Playlist("Jazz", null, List.of(song("blue")));

    @Test
    void deletesThePlaylistAndOnlyItsSongs() {
        StepVerifier.create(adapter.save(ROCK).then(adapter.save(JAZZ)).then(adapter.deleteByName("Rock")))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(adapter.findAll())
                .expectNext(JAZZ)
                .verifyComplete();
        StepVerifier.create(column("SELECT COUNT(*) FROM song"))
                .expectNext("1")
                .verifyComplete();
    }

    @Test
    void returnsFalseWhenThePlaylistDoesNotExist() {
        StepVerifier.create(adapter.deleteByName("Jazz"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void doesNotBringBackOldSongsWhenTheNameIsReused() {
        Playlist empty = new Playlist("Rock", null, List.of());

        StepVerifier.create(adapter.save(ROCK)
                        .then(adapter.deleteByName("Rock"))
                        .then(adapter.save(empty))
                        .then(adapter.findByName("Rock")))
                .expectNext(empty)
                .verifyComplete();
    }

    @Test
    void keepsTheSongsWhenThePlaylistCannotBeDeleted() {
        // A reference from another table makes the second delete of the transaction fail.
        StepVerifier.create(adapter.save(ROCK)
                        .then(execute("CREATE TABLE test_reference (playlist_id BIGINT REFERENCES playlist (id))"))
                        .then(execute("INSERT INTO test_reference SELECT id FROM playlist")))
                .verifyComplete();

        StepVerifier.create(adapter.deleteByName("Rock"))
                .expectError()
                .verify();

        StepVerifier.create(adapter.findByName("Rock"))
                .expectNext(ROCK)
                .verifyComplete();
        StepVerifier.create(execute("DROP TABLE test_reference"))
                .verifyComplete();
    }

    private static Song song(String title) {
        return new Song(title, null, null, null, null);
    }
}
