package com.quipux.playlist.infrastructure.out.catalog;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The catalog in PostgreSQL with pgvector. It is the only blocking part of the application: every call, to the
 * database or to the embeddings, runs on {@code boundedElastic}.
 */
public class PgVectorSongCatalogAdapter implements SongCatalogAdapter {

    public static final String SCHEMA = "catalog";
    public static final String TABLE = "song_embedding";

    private static final String MODEL_KEY = "model";
    private static final String INDEXED =
            "SELECT id, metadata->>'" + MODEL_KEY + "' FROM " + SCHEMA + "." + TABLE;
    private static final DefaultContentFormatter TEXT_ONLY =
            DefaultContentFormatter.builder().withTextTemplate("{content}").build();

    private final SongCatalog catalog;
    private final VectorStore store;
    private final JdbcTemplate jdbc;
    private final SongSample sample;
    private final Duration retrievalTimeout;

    public PgVectorSongCatalogAdapter(
            SongCatalog catalog, VectorStore store, JdbcTemplate jdbc, SongSample sample, Duration retrievalTimeout) {
        this.catalog = catalog;
        this.store = store;
        this.jdbc = jdbc;
        this.sample = sample;
        this.retrievalTimeout = retrievalTimeout;
    }

    @Override
    public SongCatalog catalog() {
        return catalog;
    }

    // The query is written like the entries it is compared with, from the same part of the playlist the model reads.
    // The embedding and the search share one time limit. Giving up does not stop a JDBC call: the socket and query
    // limits of the connection bound it.
    @Override
    public Mono<List<UUID>> nearest(List<Song> songs, int limit) {
        return onElastic(() -> {
            String query = sample.of(songs).stream()
                    .map(CatalogText::of)
                    .collect(Collectors.joining("\n"));
            return store.similaritySearch(SearchRequest.builder().query(query).topK(limit).build()).stream()
                    .map(document -> UUID.fromString(document.getId()))
                    .toList();
        })
                .timeout(retrievalTimeout)
                .onErrorMap(CatalogFailures::translate);
    }

    @Override
    public Mono<Map<UUID, String>> indexed() {
        return blocking(() -> {
            Map<UUID, String> entries = new HashMap<>();
            RowCallbackHandler reader = row -> entries.put(
                    row.getObject(1, UUID.class), Objects.toString(row.getString(2), ""));
            jdbc.query(INDEXED, reader);
            return Map.copyOf(entries);
        });
    }

    @Override
    public Mono<Void> save(List<CatalogSong> songs) {
        return blocking(() -> {
            store.add(songs.stream().map(this::documentOf).toList());
            return null;
        });
    }

    @Override
    public Mono<Void> remove(List<UUID> ids) {
        return blocking(() -> {
            store.delete(ids.stream().map(UUID::toString).toList());
            return null;
        });
    }

    private Document documentOf(CatalogSong song) {
        Document document = new Document(
                song.id().toString(), CatalogText.of(song), Map.of(MODEL_KEY, catalog.embeddingModel()));
        document.setContentFormatter(TEXT_ONLY);
        return document;
    }

    private <T> Mono<T> blocking(Callable<T> call) {
        return onElastic(call).onErrorMap(CatalogFailures::translate);
    }

    private static <T> Mono<T> onElastic(Callable<T> call) {
        return Mono.fromCallable(call).subscribeOn(Schedulers.boundedElastic());
    }
}
