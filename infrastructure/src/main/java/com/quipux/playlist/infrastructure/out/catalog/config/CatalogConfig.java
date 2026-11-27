package com.quipux.playlist.infrastructure.out.catalog.config;

import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.infrastructure.out.catalog.FileSongCatalog;
import com.quipux.playlist.infrastructure.out.catalog.PgVectorSongCatalogAdapter;
import com.quipux.playlist.infrastructure.out.catalog.SongCatalogAdapter;
import com.quipux.playlist.infrastructure.out.catalog.UnavailableSongCatalogAdapter;
import com.quipux.playlist.infrastructure.out.genre.LocalGenreProviderAdapter;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The catalog and, when it is configured, its database. The connection to PostgreSQL is declared here and is not a
 * candidate for anything else: users and playlists stay in H2 with R2DBC (ADR-0002).
 */
@Configuration
public class CatalogConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogConfig.class);
    private static final String DATA_SOURCE = "catalogDataSource";
    private static final int EMBEDDING_DIMENSIONS = 768;
    // The index returns at most this many rows for a search; the catalog never holds more songs.
    private static final String SEARCH_WIDTH = "SET hnsw.ef_search = 500";

    @Value("${playlist.catalog.file:classpath:catalog/songs.json}")
    private Resource catalogFile;

    @Value("${playlist.genres.local-file:classpath:local-genres.properties}")
    private Resource localGenres;

    @Value("${playlist.ai.embedding-model:recomendador-embeddings}")
    private String embeddingModel;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${playlist.catalog.jdbc-url:}")
    private String jdbcUrl;

    @Value("${playlist.catalog.username:catalog}")
    private String username;

    @Value("${playlist.catalog.password:}")
    private String password;

    @Value("${playlist.catalog.retrieval-timeout:5s}")
    private Duration retrievalTimeout;

    @Value("${playlist.catalog.pool.max-size:4}")
    private int poolSize;

    @Value("${playlist.catalog.pool.connection-timeout:2s}")
    private Duration connectionTimeout;

    @Value("${playlist.catalog.pool.socket-timeout:4s}")
    private Duration socketTimeout;

    @Value("${playlist.catalog.pool.query-timeout:3s}")
    private Duration queryTimeout;

    @Value("${playlist.ai.max-songs:50}")
    private int maxSongs;

    @Value("${playlist.ai.max-field-length:100}")
    private int maxFieldLength;

    @Bean(name = DATA_SOURCE, defaultCandidate = false, destroyMethod = "close")
    HikariDataSource catalogDataSource() {
        return dataSource(settings());
    }

    // Spring AI only creates an embedding model when the AI is enabled.
    @Bean
    SongCatalogAdapter songCatalog(
            @Qualifier(DATA_SOURCE) DataSource dataSource, ObjectProvider<EmbeddingModel> embeddingModels) {
        SongCatalog catalog = new FileSongCatalog(
                catalogFile, new LocalGenreProviderAdapter(localGenres).catalog(), embeddingModel).catalog();
        EmbeddingModel embeddings = embeddingModels.getIfAvailable();
        if (embeddings == null || apiKey.isBlank()) {
            return new UnavailableSongCatalogAdapter(catalog, ErrorMessageEnum.CATALOG_DISABLED);
        }
        if (!settings().isConfigured()) {
            LOG.warn(ErrorMessageEnum.CATALOG_NOT_CONFIGURED.withCode());
            return new UnavailableSongCatalogAdapter(catalog, ErrorMessageEnum.CATALOG_NOT_CONFIGURED);
        }
        return adapter(catalog, dataSource, embeddings, new SongSample(maxSongs, maxFieldLength), queryTimeout,
                retrievalTimeout);
    }

    /**
     * A pool that only connects when it is used, so the application starts with the database stopped.
     */
    public static HikariDataSource dataSource(CatalogSettings settings) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("catalog");
        dataSource.setJdbcUrl(settings.jdbcUrl());
        dataSource.setUsername(settings.username());
        dataSource.setPassword(settings.password());
        dataSource.setMaximumPoolSize(settings.poolSize());
        dataSource.setMinimumIdle(0);
        dataSource.setInitializationFailTimeout(-1);
        dataSource.setConnectionTimeout(settings.connectionTimeout().toMillis());
        dataSource.addDataSourceProperty("connectTimeout", wholeSeconds(settings.connectionTimeout()));
        dataSource.addDataSourceProperty("socketTimeout", wholeSeconds(settings.socketTimeout()));
        dataSource.setConnectionInitSql(SEARCH_WIDTH);
        return dataSource;
    }

    /**
     * The adapter over pgvector. The schema is created by the composition, never by the application.
     */
    public static PgVectorSongCatalogAdapter adapter(
            SongCatalog catalog,
            DataSource dataSource,
            EmbeddingModel embeddings,
            SongSample sample,
            Duration queryTimeout,
            Duration retrievalTimeout) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(wholeSeconds(queryTimeout));
        PgVectorStore store = PgVectorStore.builder(jdbc, embeddings)
                .schemaName(PgVectorSongCatalogAdapter.SCHEMA)
                .vectorTableName(PgVectorSongCatalogAdapter.TABLE)
                .dimensions(EMBEDDING_DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .idType(PgVectorStore.PgIdType.UUID)
                .initializeSchema(false)
                .build();
        store.afterPropertiesSet();
        return new PgVectorSongCatalogAdapter(catalog, store, jdbc, sample, retrievalTimeout);
    }

    private CatalogSettings settings() {
        return new CatalogSettings(
                jdbcUrl, username, password, poolSize, connectionTimeout, socketTimeout, queryTimeout);
    }

    private static int wholeSeconds(Duration duration) {
        return (int) Math.max(1, duration.toSeconds());
    }
}
