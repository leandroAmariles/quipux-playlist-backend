package com.quipux.playlist.application.usecase;

import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import java.util.List;
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

    @Mock
    private PlaylistRepositoryPort repository;

    @InjectMocks
    private CreatePlaylistUseCase useCase;

    @Test
    void savesAndReturnsThePlaylist() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.just(PLAYLIST));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectNext(PLAYLIST)
                .verifyComplete();
    }

    @Test
    void propagatesDuplicatedName() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.error(new DuplicatePlaylistException()));

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
}
