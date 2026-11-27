package com.quipux.playlist.application.usecase;

import com.quipux.playlist.application.port.in.GetGenresPort;
import com.quipux.playlist.application.port.out.GenreProviderPort;
import com.quipux.playlist.domain.exception.BusinessException;
import com.quipux.playlist.domain.exception.GenreProviderUnavailableException;
import com.quipux.playlist.domain.model.GenreCatalog;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import com.quipux.playlist.domain.model.enums.GenreSource;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GetGenresUseCase implements GetGenresPort {

    private static final Logger LOG = Loggers.getLogger(GetGenresUseCase.class);

    private final GenreProviderPort provider;
    private final GenreProviderPort fallback;

    public GetGenresUseCase(GenreProviderPort provider, GenreProviderPort fallback) {
        this.provider = provider;
        this.fallback = fallback;
    }

    @Override
    public Mono<GenreCatalog> getGenres() {
        return Mono.defer(provider::genres)
                .map(genres -> new GenreCatalog(GenreSource.SPOTIFY, genres))
                .filter(catalog -> !catalog.isEmpty())
                .switchIfEmpty(Mono.error(() ->
                        new GenreProviderUnavailableException(ErrorMessageEnum.GENRE_PROVIDER_NO_GENRES)))
                .onErrorResume(this::fromTheFallback);
    }

    private Mono<GenreCatalog> fromTheFallback(Throwable error) {
        LOG.warn(ErrorMessageEnum.GENRE_LOCAL_FALLBACK.withCode(reasonOf(error)));
        return fallback.genres().map(genres -> new GenreCatalog(GenreSource.LOCAL, genres));
    }

    private static String reasonOf(Throwable error) {
        return error instanceof BusinessException ? error.getMessage() : error.getClass().getSimpleName();
    }
}
