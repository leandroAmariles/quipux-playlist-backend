package com.quipux.playlist.infrastructure.out.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openai.errors.OpenAIIoException;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.SongSample;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PgVectorSongCatalogFailureTest {

    private static final SongCatalog CATALOG = new SongCatalog(
            List.of(CatalogSong.of("Take Five", "Brubeck", "Time Out", "1959", "jazz")), "embeddings");
    private static final List<Song> SONGS = List.of(new Song("Naima", null, null, null, null));

    @Mock
    private VectorStore store;

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void stopsWaitingForASlowStoreAtTheRetrievalTimeLimit() {
        when(store.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(2_000);
            return List.of();
        });

        StepVerifier.create(adapter(Duration.ofMillis(200)).nearest(SONGS, 20))
                .expectErrorSatisfies(error -> assertFailure(error, ErrorMessageEnum.CATALOG_TIMEOUT))
                .verify(Duration.ofSeconds(1));
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                failure(new OpenAIIoException("SECRET-DETAIL"), ErrorMessageEnum.CATALOG_EMBEDDINGS_UNAVAILABLE),
                // The store wraps what the embeddings client throws.
                failure(new IllegalStateException("SECRET-DETAIL", new OpenAIIoException("SECRET-DETAIL")),
                        ErrorMessageEnum.CATALOG_EMBEDDINGS_UNAVAILABLE),
                failure(new DataAccessResourceFailureException("SECRET-DETAIL"),
                        ErrorMessageEnum.CATALOG_UNREACHABLE),
                // A vector of another size.
                failure(new DataIntegrityViolationException("SECRET-DETAIL"), ErrorMessageEnum.CATALOG_UNREACHABLE));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void translatesEveryFailureWithoutItsCauseOrItsText(RuntimeException failure, ErrorMessageEnum expected) {
        when(store.similaritySearch(any(SearchRequest.class))).thenThrow(failure);

        StepVerifier.create(adapter(Duration.ofSeconds(5)).nearest(SONGS, 20))
                .expectErrorSatisfies(error -> assertFailure(error, expected))
                .verify(Duration.ofSeconds(5));
    }

    private static Arguments failure(
            RuntimeException failure, ErrorMessageEnum expected) {
        return Arguments.of(failure, expected);
    }

    private PgVectorSongCatalogAdapter adapter(Duration retrievalTimeout) {
        return new PgVectorSongCatalogAdapter(CATALOG, store, jdbc, new SongSample(50, 100), retrievalTimeout);
    }

    private static void assertFailure(Throwable error, ErrorMessageEnum expected) {
        assertThat(error)
                .isInstanceOf(SongCatalogUnavailableException.class)
                .hasMessage(expected.format())
                .hasNoCause();
    }
}
