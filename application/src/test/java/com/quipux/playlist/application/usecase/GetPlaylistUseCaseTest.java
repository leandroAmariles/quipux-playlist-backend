package com.quipux.playlist.application.usecase;

import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
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
class GetPlaylistUseCaseTest {

    @Mock
    private PlaylistRepositoryPort repository;

    @InjectMocks
    private GetPlaylistUseCase useCase;

    @Test
    void returnsThePlaylist() {
        Playlist rock = new Playlist("Rock", null, List.of());
        when(repository.findByName("Rock")).thenReturn(Mono.just(rock));

        StepVerifier.create(useCase.getByName("Rock"))
                .expectNext(rock)
                .verifyComplete();
    }

    @Test
    void failsWhenThePlaylistDoesNotExist() {
        when(repository.findByName("Jazz")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getByName("Jazz"))
                .expectError(PlaylistNotFoundException.class)
                .verify();
    }
}
