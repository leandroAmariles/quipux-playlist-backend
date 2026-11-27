package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.enums.PlaylistEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeletePlaylistUseCaseTest {

    @Mock
    private PlaylistRepositoryPort repository;

    @Mock
    private PlaylistEventPublisherPort eventPublisher;

    @InjectMocks
    private DeletePlaylistUseCase useCase;

    @Test
    void completesAndPublishesDeletedEvent() {
        when(repository.deleteByName("Rock")).thenReturn(Mono.just(true));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.delete("Rock")).verifyComplete();

        ArgumentCaptor<PlaylistEvent> captor = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(PlaylistEventType.PLAYLIST_DELETED);
        assertThat(captor.getValue().playlistName()).isEqualTo("Rock");
        assertThat(captor.getValue().songCount()).isNull();
    }

    @Test
    void completesEvenWhenEventPublisherFails() {
        when(repository.deleteByName("Rock")).thenReturn(Mono.just(true));
        when(eventPublisher.publish(any())).thenReturn(Mono.error(new RuntimeException("kafka down")));

        StepVerifier.create(useCase.delete("Rock")).verifyComplete();
    }

    @Test
    void failsWithoutPublishingWhenThePlaylistDoesNotExist() {
        when(repository.deleteByName("Jazz")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.delete("Jazz"))
                .expectError(PlaylistNotFoundException.class)
                .verify();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void propagatesRepositoryErrorWithoutPublishing() {
        when(repository.deleteByName("Rock")).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(useCase.delete("Rock"))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(eventPublisher);
    }
}
