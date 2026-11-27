package com.quipux.playlist.application.usecase;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.GenreProviderPort;
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

    private GetGenresUseCase useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new GetGenresUseCase(spotify, local);
    }

    @Test
    void returnsTheSpotifyGenresWithoutReadingTheLocalList() {
        when(spotify.genres()).thenReturn(Mono.just(List.of("rock", "pop")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(new GenreCatalog(GenreSource.SPOTIFY, List.of("rock", "pop")))
                .verifyComplete();
        verifyNoInteractions(local);
    }

    @Test
    void fallsBackWhenSpotifyIsUnavailable() {
        when(spotify.genres()).thenReturn(Mono.error(
                new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_TIMEOUT)));

        expectLocalCatalog();
    }

    @Test
    void fallsBackOnAnUnexpectedError() {
        when(spotify.genres()).thenReturn(Mono.error(new IllegalStateException("boom")));

        expectLocalCatalog();
    }

    @Test
    void fallsBackWhenNoSpotifyGenreIsUsable() {
        when(spotify.genres()).thenReturn(Mono.just(List.of(" ", "g".repeat(101))));

        expectLocalCatalog();
    }

    @Test
    void fallsBackWhenSpotifyCompletesWithoutAnswer() {
        when(spotify.genres()).thenReturn(Mono.empty());

        expectLocalCatalog();
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
    }

    private void expectLocalCatalog() {
        when(local.genres()).thenReturn(Mono.just(List.of("salsa")));

        StepVerifier.create(useCase.getGenres())
                .expectNext(LOCAL_CATALOG)
                .verifyComplete();
    }
}
