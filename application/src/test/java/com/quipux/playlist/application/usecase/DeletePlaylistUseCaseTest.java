package com.quipux.playlist.application.usecase;

import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeletePlaylistUseCaseTest {

    @Mock
    private PlaylistRepositoryPort repository;

    @InjectMocks
    private DeletePlaylistUseCase useCase;

    @Test
    void completesWhenThePlaylistIsDeleted() {
        when(repository.deleteByName("Rock")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.delete("Rock")).verifyComplete();
    }

    @Test
    void failsWhenThePlaylistDoesNotExist() {
        when(repository.deleteByName("Jazz")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.delete("Jazz"))
                .expectError(PlaylistNotFoundException.class)
                .verify();
    }

    @Test
    void propagatesRepositoryError() {
        when(repository.deleteByName("Rock")).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(useCase.delete("Rock"))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
