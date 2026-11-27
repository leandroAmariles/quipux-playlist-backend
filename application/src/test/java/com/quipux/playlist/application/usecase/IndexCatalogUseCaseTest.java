package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.CatalogIndexPort;
import com.quipux.playlist.application.port.out.SongCatalogPort;
import com.quipux.playlist.domain.exception.SongCatalogUnavailableException;
import com.quipux.playlist.domain.model.CatalogSong;
import com.quipux.playlist.domain.model.IndexReport;
import com.quipux.playlist.domain.model.SongCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndexCatalogUseCaseTest {

    private static final List<CatalogSong> SONGS = IntStream.range(0, 5)
            .mapToObj(number -> CatalogSong.of("Song " + number, "Artist", "Album", "2000", "rock"))
            .collect(Collectors.toList());
    private static final UUID STALE = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final String MODEL = "embeddings";

    @Mock
    private SongCatalogPort catalog;

    @Mock
    private CatalogIndexPort index;

    private IndexCatalogUseCase useCase;

    @BeforeEach
    void createUseCase() {
        when(catalog.catalog()).thenReturn(new SongCatalog(SONGS, MODEL));
        when(index.save(anyList())).thenReturn(Mono.empty());
        when(index.remove(anyList())).thenReturn(Mono.empty());
        useCase = new IndexCatalogUseCase(catalog, index, 2);
    }

    @Test
    void savesInBatchesAndThenRemovesWhatIsLeftOver() {
        when(index.indexed()).thenReturn(Mono.just(Map.of(STALE, MODEL)));

        StepVerifier.create(useCase.index())
                .expectNext(new IndexReport(5, 0, 1))
                .verifyComplete();

        InOrder order = inOrder(index);
        order.verify(index).save(SONGS.subList(0, 2));
        order.verify(index).save(SONGS.subList(2, 4));
        order.verify(index).save(SONGS.subList(4, 5));
        order.verify(index).remove(List.of(STALE));
    }

    @Test
    void doesNothingWhenTheIndexIsCurrent() {
        when(index.indexed()).thenReturn(Mono.just(
                SONGS.stream().collect(Collectors.toMap(CatalogSong::id, song -> MODEL))));

        StepVerifier.create(useCase.index())
                .expectNext(new IndexReport(0, 5, 0))
                .verifyComplete();

        verify(index, never()).save(any());
        verify(index, never()).remove(any());
    }

    @Test
    void stopsAtTheFirstFailureWithoutRemovingAnything() {
        SongCatalogUnavailableException failure =
                new SongCatalogUnavailableException(ErrorMessageEnum.CATALOG_EMBEDDINGS_UNAVAILABLE);
        when(index.indexed()).thenReturn(Mono.just(Map.of(STALE, MODEL)));
        when(index.save(SONGS.subList(2, 4))).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.index())
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();

        verify(index, never()).save(SONGS.subList(4, 5));
        verify(index, never()).remove(any());
    }

    @Test
    void passesOnTheFailureToReadTheIndex() {
        when(index.indexed()).thenReturn(Mono.error(
                new SongCatalogUnavailableException(ErrorMessageEnum.CATALOG_UNREACHABLE)));

        StepVerifier.create(useCase.index())
                .expectError(SongCatalogUnavailableException.class)
                .verify();

        verify(index, never()).save(any());
    }
}
