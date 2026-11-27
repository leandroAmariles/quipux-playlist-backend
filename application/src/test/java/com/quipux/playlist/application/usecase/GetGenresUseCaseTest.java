package com.quipux.playlist.application.usecase;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.GenreCachePort;
import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.domain.exception.GenreCacheUnavailableException;
import com.quipux.playlist.domain.exception.GenreProviderUnavailableException;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetGenresUseCaseTest {

    private static final GenreCatalog LOCAL_CATALOG = new GenreCatalog(GenreSource.LOCAL, List.of("salsa"));

    @Mock
    private GenreProviderPort spotify;

    @Mock
    private GenreProviderPort local;

    @Mock
    private GenreCachePort cache;

    private GetGenresUseCase useCase;

    @BeforeEach
    void createUseCase() {
        lenient().when(cache.find()).thenReturn(Mono.empty());
        lenient().when(cache.save(anyList())).thenReturn(Mono.empty());
        useCase = new GetGenresUseCase(spotify, local, cache);
    }

    @Test
    void returnsCachedGenresWithoutCallingProviders() {
        when(cache.find()).thenReturn(Mono.just(List.of("rock", "pop")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(new GenreCatalog(GenreSource.SPOTIFY, List.of("rock", "pop")))
                .verifyComplete();
        verifyNoInteractions(spotify, local);
        verify(cache, never()).save(anyList());
    }

    @Test
    void returnsTheSpotifyGenresAndCachesSanitizedListWithoutReadingLocal() {
        when(spotify.genres()).thenReturn(Mono.just(List.of("rock", " ", "pop", "Rock")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(new GenreCatalog(GenreSource.SPOTIFY, List.of("rock", "pop")))
                .verifyComplete();
        verify(cache).save(List.of("rock", "pop"));
        verifyNoInteractions(local);
    }

    @Test
    void fallsBackWhenSpotifyIsUnavailableWithoutCachingLocal() {
        when(spotify.genres()).thenReturn(Mono.error(
                new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_TIMEOUT)));

        expectLocalCatalog();
        verify(cache, never()).save(anyList());
    }

    @Test
    void fallsBackOnAnUnexpectedErrorWithoutCachingLocal() {
        when(spotify.genres()).thenReturn(Mono.error(new IllegalStateException("boom")));

        expectLocalCatalog();
        verify(cache, never()).save(anyList());
    }

    @Test
    void fallsBackWhenNoSpotifyGenreIsUsableWithoutCachingLocal() {
        when(spotify.genres()).thenReturn(Mono.just(List.of(" ", "g".repeat(101))));

        expectLocalCatalog();
        verify(cache, never()).save(anyList());
    }

    @Test
    void fallsBackWhenSpotifyCompletesWithoutAnswerWithoutCachingLocal() {
        when(spotify.genres()).thenReturn(Mono.empty());

        expectLocalCatalog();
        verify(cache, never()).save(anyList());
    }

    @Test
    void asksSpotifyAgainAfterAFailure() {
        when(spotify.genres())
                .thenReturn(Mono.error(new IllegalStateException("boom")))
                .thenReturn(Mono.just(List.of("rock")));
        when(local.genres()).thenReturn(Mono.just(List.of("salsa")));

        StepVerifier.create(useCase.getGenres().concatWith(useCase.getGenres()).map(GenreCatalog::source))
                .expectNext(GenreSource.LOCAL, GenreSource.SPOTIFY)
                .verifyComplete();
        verify(cache).save(List.of("rock"));
    }

    @Test
    void continuesWithSpotifyWhenCacheReadFails() {
        when(cache.find()).thenReturn(Mono.error(
                new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_TIMEOUT, "read")));
        when(spotify.genres()).thenReturn(Mono.just(List.of("jazz")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(new GenreCatalog(GenreSource.SPOTIFY, List.of("jazz")))
                .verifyComplete();
        verify(cache).save(List.of("jazz"));
        verifyNoInteractions(local);
    }

    @Test
    void stillReturnsSpotifyGenresWhenCacheWriteFails() {
        when(spotify.genres()).thenReturn(Mono.just(List.of("jazz")));
        when(cache.save(List.of("jazz"))).thenReturn(Mono.error(
                new GenreCacheUnavailableException(ErrorMessageEnum.GENRE_CACHE_UNAVAILABLE, "write")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(new GenreCatalog(GenreSource.SPOTIFY, List.of("jazz")))
                .verifyComplete();
        verifyNoInteractions(local);
    }

    private void expectLocalCatalog() {
        when(local.genres()).thenReturn(Mono.just(List.of("salsa")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(LOCAL_CATALOG)
                .verifyComplete();
    }
}
