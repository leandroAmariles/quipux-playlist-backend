package com.quipux.playlist.infrastructure.out.persistence;

import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class PlaylistPersistenceCreateTest extends PersistenceTestSupport {

    @Test
    void savesPlaylistWithItsSongsInOrder() {
        Playlist playlist = new Playlist("Rock", "description",
                List.of(song("zeta"), new Song("alpha", "artist", "album", "1999", "rock"), song("zeta")));

        StepVerifier.create(adapter.save(playlist))
                .expectNext(playlist)
                .verifyComplete();

        StepVerifier.create(column("SELECT description FROM playlist WHERE name = 'Rock'"))
                .expectNext("description")
                .verifyComplete();
        StepVerifier.create(column("SELECT title FROM song ORDER BY id"))
                .expectNext("zeta", "alpha", "zeta")
                .verifyComplete();
        StepVerifier.create(column("SELECT artist || '|' || album || '|' || release_year || '|' || genre "
                        + "FROM song WHERE title = 'alpha'"))
                .expectNext("artist|album|1999|rock")
                .verifyComplete();
    }

    @Test
    void rejectsDuplicatedNameWithoutChangingTheExistingPlaylist() {
        StepVerifier.create(adapter.save(new Playlist("Rock", "original", List.of(song("first")))))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(adapter.save(new Playlist("Rock", "other", List.of(song("second")))))
                .expectError(DuplicatePlaylistException.class)
                .verify();

        StepVerifier.create(column("SELECT description FROM playlist"))
                .expectNext("original")
                .verifyComplete();
        StepVerifier.create(column("SELECT title FROM song"))
                .expectNext("first")
                .verifyComplete();
    }

    @Test
    void treatsCaseAndSurroundingSpacesAsDifferentNames() {
        StepVerifier.create(adapter.save(playlist("Rock"))
                        .then(adapter.save(playlist("rock")))
                        .then(adapter.save(playlist(" Rock "))))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(column("SELECT name FROM playlist ORDER BY id"))
                .expectNext("Rock", "rock", " Rock ")
                .verifyComplete();
    }

    @Test
    void savesNothingWhenOneSongCannotBeStored() {
        Playlist playlist = new Playlist("Rock", null, List.of(song("first"), song("rejected")));

        StepVerifier.create(execute("ALTER TABLE song ADD CONSTRAINT ck_test_title CHECK (title <> 'rejected')")
                        .then(adapter.save(playlist))
                        .then())
                .expectError()
                .verify();

        StepVerifier.create(column("SELECT COUNT(*) FROM playlist").concatWith(column("SELECT COUNT(*) FROM song")))
                .expectNext("0", "0")
                .verifyComplete();
        StepVerifier.create(execute("ALTER TABLE song DROP CONSTRAINT ck_test_title"))
                .verifyComplete();
    }

    private static Playlist playlist(String name) {
        return new Playlist(name, null, List.of());
    }

    private static Song song(String title) {
        return new Song(title, null, null, null, null);
    }
}
