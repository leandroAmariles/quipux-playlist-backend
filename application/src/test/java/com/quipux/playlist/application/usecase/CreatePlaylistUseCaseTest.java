package com.quipux.playlist.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.DuplicatePlaylistException;
import com.quipux.playlist.domain.exception.EventPublishException;
import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.Song;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import com.quipux.playlist.domain.model.enums.PlaylistEventType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private PlaylistEventPublisherPort eventPublisher;

    @InjectMocks
    private CreatePlaylistUseCase useCase;

    @Test
    void savesAndPublishesCreatedEvent() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.just(PLAYLIST));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectNext(PLAYLIST)
                .verifyComplete();

        verifyNoInteractions(getGenres);
        ArgumentCaptor<PlaylistEvent> captor = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(PlaylistEventType.PLAYLIST_CREATED);
        assertThat(captor.getValue().playlistName()).isEqualTo("Rock");
        assertThat(captor.getValue().songCount()).isZero();
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
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.create(playlist))
                .expectNext(playlist)
                .verifyComplete();
        assertThat(catalogRequests).hasValue(1);
        ArgumentCaptor<PlaylistEvent> captor = ArgumentCaptor.forClass(PlaylistEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().songCount()).isEqualTo(3);
    }

    @Test
    void succeedsWhenEventPublisherFails() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.just(PLAYLIST));
        when(eventPublisher.publish(any())).thenReturn(Mono.error(
                new EventPublishException(ErrorMessageEnum.EVENT_PUBLISH_UNAVAILABLE, "PlaylistCreated", "Rock")));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectNext(PLAYLIST)
                .verifyComplete();
    }

    @Test
    void doesNotSaveOrPublishAnUnknownGenre() {
        when(getGenres.getGenres()).thenReturn(Mono.just(CATALOG));

        StepVerifier.create(useCase.create(playlistOf("rock", "no-existe")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidPlaylistException.class)
                        .hasMessage("A song genre is not in the genre catalog"))
                .verify();
        verifyNoInteractions(repository, eventPublisher);
    }

    @Test
    void propagatesDuplicatedNameWithoutPublishing() {
        when(repository.save(any())).thenReturn(Mono.error(new DuplicatePlaylistException()));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectError(DuplicatePlaylistException.class)
                .verify();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void propagatesUnexpectedErrorWithoutPublishing() {
        when(repository.save(PLAYLIST)).thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(useCase.create(PLAYLIST))
                .expectError(IllegalStateException.class)
                .verify();
        verifyNoInteractions(eventPublisher);
    }

    private static Playlist playlistOf(String... genres) {
        return new Playlist("Rock", null, java.util.Arrays.stream(genres)
                .map(genre -> new Song(null, null, null, null, genre))
                .toList());
    }
}
