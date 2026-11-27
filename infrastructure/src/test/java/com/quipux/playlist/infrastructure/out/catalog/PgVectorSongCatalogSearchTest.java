package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PgVectorSongCatalogSearchTest {

    private static final SongCatalog CATALOG = new SongCatalog(
            List.of(CatalogSong.of("Take Five", "Brubeck", "Time Out", "1959", "jazz")), "embeddings");
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    // Two songs of at most six characters per field.
    private static final SongSample SAMPLE = new SongSample(2, 6);

    @Mock
    private VectorStore store;

    @Mock
    private JdbcTemplate jdbc;

    private final AtomicReference<String> thread = new AtomicReference<>();

    @Test
    void searchesWithTheSampleOfThePlaylistAndKeepsTheOrderOnABoundedElasticThread() {
        when(store.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return List.of(document(SECOND), document(FIRST));
        });
        List<Song> songs = List.of(
                new Song("Blue in Green", "Miles", "Kind", "1959", "jazz"),
                new Song("Naima", null, null, null, null),
                new Song("Left out", "Artist", null, null, null));

        StepVerifier.create(adapter().nearest(songs, 23))
                .expectNext(List.of(SECOND, FIRST))
                .verifyComplete();

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(store).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery())
                .isEqualTo("Blue i | Miles | Kind | 1959 | jazz\nNaima |  |  |  | ");
        assertThat(request.getValue().getTopK()).isEqualTo(23);
        assertThat(thread.get()).startsWith("boundedElastic");
    }

    private PgVectorSongCatalogAdapter adapter() {
        return new PgVectorSongCatalogAdapter(CATALOG, store, jdbc, SAMPLE, Duration.ofSeconds(5));
    }

    private static Document document(UUID id) {
        return new Document(id.toString(), "text", java.util.Map.of());
    }
}
