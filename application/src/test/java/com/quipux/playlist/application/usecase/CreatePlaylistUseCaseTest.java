package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.GenreSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CreatePlaylistUseCaseTest {

    private static final Playlist PLAYLIST = new Playlist("Rock", "description", List.of());
    private static final GenreCatalog CATALOG = new GenreCatalog(GenreSource.LOCAL, List.of("rock", "pop"));

    @Mock
    private PlaylistRepositoryPort repository;

    @Mock
    private GetGenresPort getGenres;

    @InjectMocks
    private CreatePlaylistUseCase useCase;

    @Test
    void savesAndReturnsThePlaylist() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.just(PLAYLIST));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectNext(PLAYLIST)
                .verifyComplete();
        verifyNoInteractions(getGenres);
    }

    @Test
    void savesAfterCheckingTheCatalogOnce() {
        Playlist playlist = playlistOf("rock", "Pop", "rock");
        AtomicInteger catalogRequests = new AtomicInteger();
        when(getGenres.getGenres()).thenReturn(Mono.fromSupplier(() -> {
            catalogRequests.incrementAndGet();
            return CATALOG;
        }));
        when(repository.save(playlist)).thenReturn(Mono.just(playlist));

        StepVerifier.create(useCase.create(playlist))
                .expectNext(playlist)
                .verifyComplete();
        assertThat(catalogRequests).hasValue(1);
    }

    @Test
    void doesNotSaveAnUnknownGenre() {
        when(getGenres.getGenres()).thenReturn(Mono.just(CATALOG));

        StepVerifier.create(useCase.create(playlistOf("rock", "no-existe")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidPlaylistException.class)
                        .hasMessage("A song genre is not in the genre catalog"))
                .verify();
        verifyNoInteractions(repository);
    }

    @Test
    void propagatesDuplicatedName() {
        when(repository.save(any())).thenReturn(Mono.error(new DuplicatePlaylistException()));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectError(DuplicatePlaylistException.class)
                .verify();
    }

    @Test
    void propagatesUnexpectedError() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private static Playlist playlistOf(String... genres) {
        return new Playlist("Rock", null, java.util.Arrays.stream(genres)
                .map(genre -> new Song(null, null, null, null, genre))
                .toList());
    }
}
