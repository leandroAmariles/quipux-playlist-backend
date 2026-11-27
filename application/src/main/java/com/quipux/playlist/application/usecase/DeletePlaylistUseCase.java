package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.DeletePlaylistPort;
import com.quipux.playlist.application.port.out.PlaylistEventPublisherPort;
import com.quipux.playlist.application.port.out.PlaylistRepositoryPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.PlaylistNotFoundException;
import com.quipux.playlist.domain.model.PlaylistEvent;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class DeletePlaylistUseCase implements DeletePlaylistPort {

    private static final Logger LOG = Loggers.getLogger(DeletePlaylistUseCase.class);

    private final PlaylistRepositoryPort repository;
    private final PlaylistEventPublisherPort eventPublisher;

    public DeletePlaylistUseCase(
            PlaylistRepositoryPort repository,
            PlaylistEventPublisherPort eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<Void> delete(String name) {
        return repository.deleteByName(name)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(PlaylistNotFoundException::new))
                .flatMap(deleted -> publishBestEffort(PlaylistEvent.deleted(name)));
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
