package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.errors.OpenAIIoException;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PgVectorSongCatalogIndexTest {

    private static final String ELASTIC_THREAD = "boundedElastic";
    private static final CatalogSong SONG =
            CatalogSong.of("Take Five", "The Dave Brubeck Quartet", "Time Out", "1959", "jazz");
    private static final SongCatalog CATALOG = new SongCatalog(List.of(SONG), "embeddings");

    @Mock
    private VectorStore store;

    @Mock
    private JdbcTemplate jdbc;

    private final AtomicReference<String> thread = new AtomicReference<>();

    private PgVectorSongCatalogAdapter adapter;

    @BeforeEach
    void createAdapter() {
        adapter = new PgVectorSongCatalogAdapter(CATALOG, store, jdbc, new SongSample(50, 100), Duration.ofSeconds(5));
    }

    @Test
    void givesTheCatalogItWasCreatedWith() {
        assertThat(adapter.catalog()).isSameAs(CATALOG);
    }

    @Test
    void readsTheIdentifierAndTheModelOfEveryEntryOnABoundedElasticThread() throws Exception {
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            RowCallbackHandler rows = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getObject(1, UUID.class)).thenReturn(SONG.id());
            when(row.getString(2)).thenReturn(null);
            rows.processRow(row);
            return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class));

        StepVerifier.create(adapter.indexed())
                .expectNext(Map.of(SONG.id(), ""))
                .verifyComplete();

        assertThat(thread.get()).startsWith(ELASTIC_THREAD);
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesTheTextOfEachSongWithItsModelOnABoundedElasticThread() {
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return null;
        }).when(store).add(anyList());

        StepVerifier.create(adapter.save(List.of(SONG))).verifyComplete();

        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        verify(store).add(documents.capture());
        Document document = documents.getValue().get(0);
        String text = "Take Five | The Dave Brubeck Quartet | Time Out | 1959 | jazz";
        assertThat(document.getId()).isEqualTo(SONG.id().toString());
        assertThat(document.getText()).isEqualTo(text);
        assertThat(document.getMetadata()).isEqualTo(Map.of("model", "embeddings"));
        // What is compared is the text alone, without metadata or blank lines before it.
        assertThat(document.getFormattedContent(MetadataMode.NONE)).isEqualTo(text);
        assertThat(thread.get()).startsWith(ELASTIC_THREAD);
    }

    @Test
    void removesTheEntriesOnABoundedElasticThread() {
        doAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return null;
        }).when(store).delete(List.of(SONG.id().toString()));

        StepVerifier.create(adapter.remove(List.of(SONG.id()))).verifyComplete();

        assertThat(thread.get()).startsWith(ELASTIC_THREAD);
    }

    @Test
    void reportsAFailureOfTheDatabaseWithoutItsCause() {
        doThrow(new DataAccessResourceFailureException("SECRET-DETAIL")).when(store).add(anyList());

        StepVerifier.create(adapter.save(List.of(SONG)))
                .expectErrorSatisfies(error -> assertFailure(error, ErrorMessageEnum.CATALOG_UNREACHABLE))
                .verify();
    }

    @Test
    void reportsAFailureOfTheEmbeddingsWithoutItsCause() {
        doThrow(new OpenAIIoException("SECRET-DETAIL")).when(store).add(anyList());

        StepVerifier.create(adapter.save(List.of(SONG)))
                .expectErrorSatisfies(
                        error -> assertFailure(error, ErrorMessageEnum.CATALOG_EMBEDDINGS_UNAVAILABLE))
                .verify();
    }

    private static void assertFailure(Throwable error, ErrorMessageEnum expected) {
        assertThat(error)
                .isInstanceOf(SongCatalogUnavailableException.class)
                .hasMessage(expected.format())
                .hasNoCause();
    }
}
