package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.CreatePlaylistPort;
import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.InvalidPlaylistException;
import com.quipux.playlist.domain.model.Playlist;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class CreatePlaylistUseCase implements CreatePlaylistPort {

    private static final Logger LOG = Loggers.getLogger(CreatePlaylistUseCase.class);

    private final PlaylistRepositoryPort repository;
    private final GetGenresPort getGenres;
    private final PlaylistEventPublisherPort eventPublisher;

    public CreatePlaylistUseCase(
            PlaylistRepositoryPort repository,
            GetGenresPort getGenres,
            PlaylistEventPublisherPort eventPublisher) {
        this.repository = repository;
        this.getGenres = getGenres;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Playlist> create(Playlist playlist) {
        List<String> genres = playlist.declaredGenres();
        if (genres.isEmpty()) {
            return saveAndPublish(playlist);
        }
        return getGenres.getGenres()
                .flatMap(catalog -> catalog.includesAll(genres)
                        ? saveAndPublish(playlist)
                        : Mono.error(new InvalidPlaylistException(ErrorMessageEnum.UNKNOWN_GENRE)));
    }

    private Mono<Playlist> saveAndPublish(Playlist playlist) {
        return repository.save(playlist)
                .flatMap(saved -> publishBestEffort(PlaylistEvent.created(saved)).thenReturn(saved));
    }

    private Mono<Void> publishBestEffort(PlaylistEvent event) {
        return Mono.defer(() -> eventPublisher.publish(event))
                .onErrorResume(this::ignorePublishFailure);
    }

    private Mono<Void> ignorePublishFailure(Throwable error) {
        LOG.warn(ErrorMessageEnum.EVENT_PUBLISH_IGNORED.withCode(reasonOf(error)));
        return Mono.empty();
    }

    private static String reasonOf(Throwable error) {
        return error instanceof BusinessException ? error.getMessage() : error.getClass().getSimpleName();
    }
}
