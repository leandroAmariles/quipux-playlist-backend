package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.infrastructure.out.catalog.config.CatalogConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

@Testcontainers(disabledWithoutDocker = true)
class PgVectorCatalogSearchTest {

    @Container
    private static final PostgreSQLContainer DATABASE = PgVectorContainer.create();

    private static final List<CatalogSong> SONGS = IntStream.range(0, 60)
            .mapToObj(number -> CatalogSong.of("Song " + number + ".", "Artist", "Album", "2000", "rock"))
            .toList();

    // Each song points a bit further from the query than the one before it.
    private final FakeEmbeddingModel embeddings = new FakeEmbeddingModel(PgVectorCatalogSearchTest::vectorOf);

    private HikariDataSource dataSource;
    private PgVectorSongCatalogAdapter adapter;

    @BeforeEach
    void indexTheCatalog() {
        dataSource = CatalogConfig.dataSource(PgVectorContainer.settingsOf(DATABASE));
        new JdbcTemplate(dataSource).execute("TRUNCATE catalog.song_embedding");
        adapter = CatalogConfig.adapter(new SongCatalog(SONGS, "model"), dataSource, embeddings,
                new SongSample(50, 100), Duration.ofSeconds(10), Duration.ofSeconds(10));
        StepVerifier.create(adapter.save(SONGS)).expectComplete().verify(Duration.ofSeconds(30));
    }

    @AfterEach
    void closeThePool() {
        dataSource.close();
    }

    @Test
    void givesTheNearestSongsFirstAndMoreThanTheIndexReturnsByDefault() {
        List<UUID> nearest = SONGS.stream().limit(50).map(CatalogSong::id).toList();

        StepVerifier.create(adapter.nearest(List.of(new Song("Query", null, null, null, null)), 50))
                .expectNext(nearest)
                .expectComplete()
                .verify(Duration.ofSeconds(30));
    }

    private static float[] vectorOf(String text) {
        float[] vector = new float[FakeEmbeddingModel.DIMENSIONS];
        vector[0] = 1;
        if (text.startsWith("Song ")) {
            vector[1] = (Integer.parseInt(text.substring("Song ".length(), text.indexOf('.'))) + 1) * 0.02F;
        }
        return vector;
    }
}
