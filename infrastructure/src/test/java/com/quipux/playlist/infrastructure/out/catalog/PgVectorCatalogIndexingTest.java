package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.quipux.playlist.application.usecase.IndexCatalogUseCase;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.IndexReport;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.infrastructure.out.catalog.config.CatalogConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorCatalogIndexingTest {

    @Container
    private static final PostgreSQLContainer DATABASE = PgVectorContainer.create();

    private static final List<CatalogSong> SONGS = IntStream.range(0, 5)
            .mapToObj(number -> CatalogSong.of("Song " + number, "Artist", "Album", "2000", "rock"))
            .collect(Collectors.toList());

    private final FakeEmbeddingModel embeddings = new FakeEmbeddingModel();

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheCatalog() {
        dataSource = CatalogConfig.dataSource(PgVectorContainer.settingsOf(DATABASE));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE catalog.song_embedding");
    }

    @AfterEach
    void closeThePool() {
        dataSource.close();
    }

    @Test
    void indexesTheCatalogWithOneRowPerSong() {
        index(SONGS, "model", new IndexReport(5, 0, 0));

        assertThat(rows()).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT content FROM catalog.song_embedding WHERE id = ?", String.class, SONGS.get(0).id()))
                .isEqualTo("Song 0 | Artist | Album | 2000 | rock");
        assertThat(embeddings.requested()).hasSize(5).contains("Song 0 | Artist | Album | 2000 | rock");
    }

    @Test
    void changesNothingAndAsksForNoEmbeddingsWhenRepeated() {
        index(SONGS, "model", new IndexReport(5, 0, 0));
        embeddings.requested().clear();

        index(SONGS, "model", new IndexReport(0, 5, 0));

        assertThat(rows()).isEqualTo(5);
        assertThat(embeddings.requested()).isEmpty();
    }

    @Test
    void leavesExactlyTheCurrentCatalogAfterSongsChange() {
        index(SONGS, "model", new IndexReport(5, 0, 0));
        CatalogSong changed = CatalogSong.of("Song 0", "Artist", "Album", "2001", "rock");
        List<CatalogSong> current = List.of(changed, SONGS.get(1), SONGS.get(2), SONGS.get(3));

        index(current, "model", new IndexReport(1, 3, 2));

        assertThat(jdbc.queryForList("SELECT id FROM catalog.song_embedding", UUID.class))
                .containsExactlyInAnyOrderElementsOf(current.stream().map(CatalogSong::id).toList());
    }

    @Test
    void indexesAgainWithAnotherModelWithoutDuplicating() {
        index(SONGS, "model", new IndexReport(5, 0, 0));

        index(SONGS, "other-model", new IndexReport(5, 0, 0));

        assertThat(rows()).isEqualTo(5);
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT metadata->>'model' FROM catalog.song_embedding", String.class))
                .containsExactly("other-model");
    }

    @Test
    void keepsTheSavedBatchesAfterAFailureAndCompletesThemLater() {
        FakeEmbeddingModel failing = new FakeEmbeddingModel(text -> {
            if (text.startsWith("Song 3")) {
                throw new IllegalStateException("boom");
            }
            return embeddings.embed(text);
        });

        StepVerifier.create(useCase(SONGS, "model", failing).index())
                .expectError(SongCatalogUnavailableException.class)
                .verify(Duration.ofSeconds(30));
        assertThat(rows()).isEqualTo(2);

        index(SONGS, "model", new IndexReport(3, 2, 0));
        assertThat(rows()).isEqualTo(5);
    }

    private void index(List<CatalogSong> songs, String model, IndexReport expected) {
        StepVerifier.create(useCase(songs, model, embeddings).index())
                .expectNext(expected)
                .expectComplete()
                .verify(Duration.ofSeconds(30));
    }

    private IndexCatalogUseCase useCase(List<CatalogSong> songs, String model, EmbeddingModel embeddingModel) {
        PgVectorSongCatalogAdapter adapter = CatalogConfig.adapter(new SongCatalog(songs, model), dataSource,
                embeddingModel, new SongSample(50, 100), Duration.ofSeconds(10), Duration.ofSeconds(10));
        return new IndexCatalogUseCase(adapter, adapter, 2);
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM catalog.song_embedding", Integer.class);
    }
}
