package com.quipux.playlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
class SchemaTest {

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void createsTheTables() {
        String query = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'PUBLIC' ORDER BY table_name";

        StepVerifier.create(databaseClient.sql(query).map(row -> row.get(0, String.class)).all())
                .expectNext("APP_USER", "PLAYLIST", "SONG")
                .verifyComplete();
    }

    // The catalog lives in PostgreSQL: nothing of it belongs to the database of users and playlists.
    @Test
    void createsNothingOfTheCatalog() {
        String query = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'CATALOG' OR table_name = 'SONG_EMBEDDING'";

        StepVerifier.create(databaseClient.sql(query).map(row -> row.get(0, Long.class)).one())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void acceptsSongOfAnExistingPlaylist() {
        String insertSong = "INSERT INTO song (playlist_id, title, artist, album, release_year, genre) "
                + "SELECT id, 'title', 'artist', 'album', '1999', 'rock' FROM playlist WHERE name = 'with-song'";

        StepVerifier.create(insertPlaylist("with-song")
                        .then(databaseClient.sql(insertSong).fetch().rowsUpdated()))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void rejectsDuplicatedPlaylistName() {
        expectIntegrityViolation(insertPlaylist("duplicated").then(insertPlaylist("duplicated")));
    }

    @Test
    void rejectsPlaylistWithoutName() {
        expectIntegrityViolation(execute("INSERT INTO playlist (name) VALUES (NULL)"));
    }

    @Test
    void rejectsSongWithoutPlaylist() {
        expectIntegrityViolation(execute("INSERT INTO song (playlist_id) VALUES (NULL)"));
    }

    @Test
    void rejectsSongOfUnknownPlaylist() {
        expectIntegrityViolation(execute("INSERT INTO song (playlist_id) VALUES (-1)"));
    }

    private Mono<Void> insertPlaylist(String name) {
        return databaseClient.sql("INSERT INTO playlist (name) VALUES (:name)").bind("name", name).then();
    }

    private Mono<Void> execute(String statement) {
        return databaseClient.sql(statement).then();
    }

    private static void expectIntegrityViolation(Mono<Void> operation) {
        StepVerifier.create(operation)
                .expectError(DataIntegrityViolationException.class)
                .verify();
    }
}
