package com.quipux.playlist.application.usecase;

import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.model.Playlist;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetPlaylistsUseCaseTest {

    @Mock
    private PlaylistRepositoryPort repository;

    @InjectMocks
    private GetPlaylistsUseCase useCase;

    @Test
    void returnsThePlaylistsOfTheRepository() {
        Playlist rock = new Playlist("Rock", null, List.of());
        Playlist jazz = new Playlist("Jazz", null, List.of());
        when(repository.findAll()).thenReturn(Flux.just(rock, jazz));

        StepVerifier.create(useCase.getAll())
                .expectNext(rock, jazz)
                .verifyComplete();
    }

    @Test
    void completesEmptyWithoutPlaylists() {
        when(repository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.getAll()).verifyComplete();
    }

    @Test
    void propagatesRepositoryError() {
        when(repository.findAll()).thenReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(useCase.getAll())
                .expectError(IllegalStateException.class)
                .verify();
    }
}
